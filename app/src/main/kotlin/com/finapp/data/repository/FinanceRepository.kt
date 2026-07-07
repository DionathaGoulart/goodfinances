package com.finapp.data.repository

import androidx.room.withTransaction
import com.finapp.data.CategoriasPadrao
import com.finapp.data.db.AppDatabase
import com.finapp.data.db.CartaoDao
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.ConfiguracaoPerfilDao
import com.finapp.data.db.SomaPorCategoria
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.TransacaoRecorrenteDao
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponto único de acesso aos dados financeiros.
 * ViewModels nunca falam com os DAOs diretamente.
 */
@Singleton
class FinanceRepository @Inject constructor(
    private val db: AppDatabase,
    private val transacaoDao: TransacaoDao,
    private val categoriaDao: CategoriaDao,
    private val configuracaoPerfilDao: ConfiguracaoPerfilDao,
    private val transacaoRecorrenteDao: TransacaoRecorrenteDao,
    private val cartaoDao: CartaoDao
) {

    private fun agora() = System.currentTimeMillis()

    // ---------- Transações ----------

    suspend fun inserirTransacao(transacao: Transacao): Long = transacaoDao.inserir(transacao)

    suspend fun atualizarTransacao(transacao: Transacao) =
        transacaoDao.atualizar(transacao.copy(atualizadoEm = agora()))

    /**
     * Deleção LÓGICA (tombstone) — necessária para propagar no sync.
     * Transferências entre contextos apagam as DUAS pernas juntas.
     */
    suspend fun deletarTransacao(transacao: Transacao) {
        val momento = agora()
        pernasDaTransferencia(transacao).forEach {
            transacaoDao.atualizar(it.copy(deletado = true, atualizadoEm = momento))
        }
    }

    /**
     * Marca/desmarca um lançamento como oculto da visão Membros da casa.
     * Carimba [atualizadoEm] para o sync propagar (o espelho remove o doc).
     */
    suspend fun ocultarTransacao(transacao: Transacao, oculto: Boolean) =
        transacaoDao.atualizar(transacao.copy(oculto = oculto, atualizadoEm = agora()))

    /** Desfaz uma deleção lógica (undo do swipe/modal) — o par junto. */
    suspend fun restaurarTransacao(transacao: Transacao) {
        val momento = agora()
        pernasDaTransferencia(transacao).forEach {
            transacaoDao.atualizar(it.copy(deletado = false, atualizadoEm = momento))
        }
    }

    /** A transação e, se for transferência, a perna no outro contexto. */
    private suspend fun pernasDaTransferencia(transacao: Transacao): List<Transacao> =
        if (transacao.transferenciaId.isBlank()) {
            listOf(transacao)
        } else {
            transacaoDao.listarPorTransferencia(transacao.transferenciaId)
                .ifEmpty { listOf(transacao) }
        }

    /**
     * Limpeza definitiva (Configurações > Limpar Todos os Dados).
     * Nos baldes do usuário vira tombstone — delete físico voltaria pelo
     * sync (os docs continuam no Firestore) e não sumiria da Casa nem da
     * visão Membros dos outros aparelhos. Só o espelho local CASA_MEMBROS
     * (que nunca é empurrado) pode ser apagado de verdade.
     */
    suspend fun deletarTodasTransacoes(perfil: Perfil) =
        if (perfil == Perfil.CASA_MEMBROS) {
            transacaoDao.deletarTodas(perfil)
        } else {
            transacaoDao.marcarTodasDeletadas(perfil, agora())
        }

    /**
     * Limpeza LOCAL ao sair de uma casa: apaga físico os baldes da Casa
     * (transações, categorias, recorrentes e o espelho dos membros).
     * Sem isso, entrar numa casa nova empurraria todo o histórico da
     * antiga para ela (a marca de push da casa nova começa em zero).
     */
    suspend fun limparDadosLocaisDaCasa() {
        transacaoDao.deletarTodas(Perfil.CASA)
        transacaoDao.deletarTodas(Perfil.CASA_MEMBROS)
        categoriaDao.deletarTodas(Perfil.CASA)
        transacaoRecorrenteDao.deletarTodas(Perfil.CASA)
        cartaoDao.deletarTodos(Perfil.CASA)
    }

    /** Arquivos de nota fiscal ainda referenciados — limpeza de órfãos. */
    suspend fun listarNotasFiscaisReferenciadas(): List<String> =
        transacaoDao.listarNotasFiscais()

    suspend fun obterTransacao(id: Long): Transacao? = transacaoDao.obterPorId(id)

    fun observarTransacoes(perfil: Perfil): Flow<List<Transacao>> =
        transacaoDao.observarTodas(perfil)

    fun observarTransacoesPeriodo(
        perfil: Perfil,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<List<Transacao>> = transacaoDao.observarPeriodo(perfil, inicio, fim)

    fun observarUltimasTransacoes(perfil: Perfil, limite: Int = 10): Flow<List<Transacao>> =
        transacaoDao.observarUltimas(perfil, limite)

    fun buscarTransacoes(perfil: Perfil, termo: String): Flow<List<Transacao>> =
        transacaoDao.buscar(perfil, termo)

    // ---------- Agregações (Dashboard e Análise) ----------

    fun observarSaldoTotal(perfil: Perfil): Flow<Long> =
        transacaoDao.observarSaldoTotal(perfil)

    fun observarGanhos(perfil: Perfil, inicio: LocalDate, fim: LocalDate): Flow<Long> =
        transacaoDao.observarSomaPorTipo(perfil, TipoTransacao.GANHO, inicio, fim)

    fun observarGastos(perfil: Perfil, inicio: LocalDate, fim: LocalDate): Flow<Long> =
        transacaoDao.observarSomaPorTipo(perfil, TipoTransacao.GASTO, inicio, fim)

    fun observarGastosPorCategoria(
        perfil: Perfil,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<List<SomaPorCategoria>> =
        transacaoDao.observarSomaPorCategoria(perfil, TipoTransacao.GASTO, inicio, fim)

    /** Somas por categoria de um tipo (ganho OU gasto) — gráfico da Análise. */
    fun observarSomasPorCategoria(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<List<SomaPorCategoria>> =
        transacaoDao.observarSomaPorCategoria(perfil, tipo, inicio, fim)

    suspend fun somarPorTipo(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Long = transacaoDao.somarPorTipo(perfil, tipo, inicio, fim)

    // ---------- Categorias ----------

    fun observarCategorias(perfil: Perfil): Flow<List<Categoria>> =
        categoriaDao.observarTodas(perfil)

    fun observarCategoriasAtivas(perfil: Perfil, tipo: TipoTransacao): Flow<List<Categoria>> =
        categoriaDao.observarAtivasPorTipo(perfil, tipo)

    suspend fun inserirCategoria(categoria: Categoria): Long = categoriaDao.inserir(categoria)

    suspend fun atualizarCategoria(categoria: Categoria) =
        categoriaDao.atualizar(categoria.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarCategoria(categoria: Categoria) =
        categoriaDao.atualizar(categoria.copy(deletado = true, atualizadoEm = agora()))

    /** Cria as categorias padrão na primeira vez que o perfil é usado. */
    suspend fun garantirCategoriasPadrao(perfil: Perfil) {
        // A Casa NÃO semeia localmente: quem cria a casa semeia uma vez
        // (semearCategoriasCasa) e os demais membros recebem via sync —
        // senão cada aparelho criaria as suas e duplicaria tudo.
        if (perfil == Perfil.CASA) return
        if (categoriaDao.contar(perfil) == 0) {
            categoriaDao.inserirTodas(CategoriasPadrao.para(perfil))
        }
    }

    /**
     * Garante a categoria "Transferência" nos baldes envolvidos numa
     * transferência entre contextos (saída na origem, entrada no destino).
     */
    suspend fun garantirCategoriaTransferencia(origem: Perfil, destino: Perfil) {
        garantirCategoria(origem, NOME_TRANSFERENCIA, TipoTransacao.GASTO)
        garantirCategoria(destino, NOME_TRANSFERENCIA, TipoTransacao.GANHO)
    }

    private suspend fun garantirCategoria(perfil: Perfil, nome: String, tipo: TipoTransacao) {
        garantirCategoria(perfil, nome, tipo, "#6B7280")
    }

    /**
     * Garante que existe a categoria [nome]/[tipo] no perfil (cria se faltar).
     * Usado por recursos que dependem de uma categoria fixa (ex: DAS mensal).
     */
    suspend fun garantirCategoria(
        perfil: Perfil,
        nome: String,
        tipo: TipoTransacao,
        cor: String
    ) {
        val existe = categoriaDao.listarTodas(perfil).any {
            it.nome.equals(nome, ignoreCase = true) && it.tipo == tipo
        }
        if (!existe) {
            categoriaDao.inserir(
                Categoria(nome = nome, tipo = tipo, cor = cor, perfil = perfil)
            )
        }
    }

    /** Semeia as categorias padrão da Casa — chamado apenas por quem CRIA a casa. */
    suspend fun semearCategoriasCasa() {
        if (categoriaDao.contar(Perfil.CASA) == 0) {
            categoriaDao.inserirTodas(CategoriasPadrao.para(Perfil.CASA))
        }
    }

    /**
     * Renomeia/recolore/reorça uma categoria e propaga o novo nome para o
     * histórico de transações e para as recorrências.
     */
    suspend fun renomearCategoria(
        categoria: Categoria,
        novoNome: String,
        novaCor: String,
        novoOrcamento: Long = categoria.orcamentoMensal
    ) {
        val momento = agora()
        categoriaDao.atualizar(
            categoria.copy(
                nome = novoNome,
                cor = novaCor,
                orcamentoMensal = novoOrcamento,
                atualizadoEm = momento
            )
        )
        if (novoNome != categoria.nome) {
            transacaoDao.renomearCategoria(categoria.perfil, categoria.nome, novoNome, momento)
            transacaoRecorrenteDao.renomearCategoria(
                categoria.perfil, categoria.nome, novoNome, momento
            )
        }
    }

    // ---------- Importação / Backup ----------

    suspend fun listarTransacoes(perfil: Perfil): List<Transacao> =
        transacaoDao.listarTodas(perfil)

    suspend fun listarCategorias(perfil: Perfil): List<Categoria> =
        categoriaDao.listarTodas(perfil)

    /**
     * Importa transações e categorias para o perfil.
     * [substituir] tombstona as transações atuais antes (delete físico
     * ressuscitaria pelo sync); caso contrário mescla, ignorando duplicatas
     * (mesma data + valor + categoria).
     * Uuid que existe como TOMBSTONE é restaurado com os dados do arquivo —
     * é o que faz "Restaurar do Backup" funcionar depois de uma limpeza.
     * Retorna quantas transações entraram (inseridas + restauradas).
     */
    suspend fun importarDados(
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        perfil: Perfil,
        substituir: Boolean
    ): Int {
        if (substituir) deletarTodasTransacoes(perfil)
        val momento = agora()

        // Categorias: restaura tombstones com mesmo uuid; insere só as que
        // não existem (nome+tipo ou uuid, considerando tombstones para não
        // violar o índice único)
        val linhasCategorias = categoriaDao.listarComTombstones(perfil)
        val categoriaPorUuid = linhasCategorias.associateBy { it.uuid }
        val categoriasVivas = linhasCategorias.filter { !it.deletado }
        val categoriasNovas = mutableListOf<Categoria>()
        categorias.forEach { nova ->
            val local = categoriaPorUuid[nova.uuid]
            when {
                local != null && local.deletado -> categoriaDao.atualizar(
                    nova.copy(id = local.id, perfil = perfil, deletado = false, atualizadoEm = momento)
                )
                local != null -> Unit // já existe viva
                categoriasVivas.any {
                    it.nome.equals(nova.nome, ignoreCase = true) && it.tipo == nova.tipo
                } -> Unit
                else -> categoriasNovas += nova.copy(id = 0, perfil = perfil)
            }
        }
        if (categoriasNovas.isNotEmpty()) categoriaDao.inserirTodas(categoriasNovas)

        // Transações: dedup por data + valor + categoria E por uuid;
        // uuid tombstonado = restaurar com os dados do arquivo
        val linhas = transacaoDao.listarComTombstones(perfil)
        val porUuid = linhas.associateBy { it.uuid }
        val chavesVivas = linhas
            .filter { !it.deletado }
            .map { Triple(it.data, it.valor, it.categoria) }
            .toHashSet()
        val novas = mutableListOf<Transacao>()
        var restauradas = 0
        transacoes.forEach { nova ->
            val local = porUuid[nova.uuid]
            when {
                local != null && local.deletado -> {
                    transacaoDao.atualizar(
                        nova.copy(
                            id = local.id,
                            perfil = perfil,
                            deletado = false,
                            atualizadoEm = momento,
                            // O arquivo da nota é local: mantém o que existir
                            notaFiscal = local.notaFiscal.ifBlank { nova.notaFiscal }
                        )
                    )
                    restauradas++
                }
                local != null -> Unit // já existe viva
                Triple(nova.data, nova.valor, nova.categoria) in chavesVivas -> Unit
                else -> novas += nova.copy(id = 0, perfil = perfil)
            }
        }
        if (novas.isNotEmpty()) transacaoDao.inserirTodas(novas)
        return novas.size + restauradas
    }

    // ---------- Cartões de crédito ----------

    fun observarCartoes(perfil: Perfil): Flow<List<Cartao>> =
        cartaoDao.observarTodos(perfil)

    suspend fun listarCartoes(perfil: Perfil): List<Cartao> = cartaoDao.listarTodos(perfil)

    suspend fun inserirCartao(cartao: Cartao): Long = cartaoDao.inserir(cartao)

    suspend fun atualizarCartao(cartao: Cartao) =
        cartaoDao.atualizar(cartao.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarCartao(cartao: Cartao) =
        cartaoDao.atualizar(cartao.copy(deletado = true, atualizadoEm = agora()))

    /**
     * Vencimento da fatura em que uma compra de [dataCompra] entra.
     * Compra até o dia do fechamento cai na fatura que fecha no mês da compra;
     * depois do fechamento, na fatura do mês seguinte. O vencimento fica no
     * mesmo mês do fechamento se o dia de vencimento for depois do fechamento,
     * senão no mês seguinte.
     */
    fun vencimentoFatura(cartao: Cartao, dataCompra: LocalDate): LocalDate {
        val fech = cartao.diaFechamento
        val venc = cartao.diaVencimento
        val mesFecha = if (dataCompra.dayOfMonth <= fech) {
            YearMonth.from(dataCompra)
        } else {
            YearMonth.from(dataCompra).plusMonths(1)
        }
        val mesVence = if (venc > fech) mesFecha else mesFecha.plusMonths(1)
        return mesVence.atDay(venc.coerceAtMost(mesVence.lengthOfMonth()))
    }

    // ---------- Configuração do perfil ----------

    fun observarConfiguracao(perfil: Perfil): Flow<ConfiguracaoPerfil?> =
        configuracaoPerfilDao.observar(perfil)

    suspend fun salvarConfiguracao(configuracao: ConfiguracaoPerfil) =
        configuracaoPerfilDao.salvar(configuracao)

    // ---------- Transações recorrentes ----------

    fun observarRecorrentesAtivas(perfil: Perfil): Flow<List<TransacaoRecorrente>> =
        transacaoRecorrenteDao.observarAtivas(perfil)

    suspend fun listarRecorrentesAtivas(perfil: Perfil): List<TransacaoRecorrente> =
        transacaoRecorrenteDao.listarAtivas(perfil)

    suspend fun inserirRecorrente(recorrente: TransacaoRecorrente): Long =
        transacaoRecorrenteDao.inserir(recorrente)

    suspend fun atualizarRecorrente(recorrente: TransacaoRecorrente) =
        transacaoRecorrenteDao.atualizar(recorrente.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarRecorrente(recorrente: TransacaoRecorrente) =
        transacaoRecorrenteDao.atualizar(
            recorrente.copy(deletado = true, atualizadoEm = agora())
        )

    /**
     * Lança as transações recorrentes vencidas e reagenda cada uma
     * conforme a frequência. Chamar ao abrir o app.
     */
    companion object {
        /** Categoria automática das transferências entre contextos. */
        const val NOME_TRANSFERENCIA = "Transferência"
    }

    /**
     * [autorCasa]/[autorCasaUid]: autoria carimbada nos lançamentos gerados
     * no balde CASA (sem isso qualquer membro poderia editá-los).
     * Cada recorrência roda numa transação do Room: lançar as ocorrências e
     * reagendar é atômico — cancelamento no meio não duplica lançamentos.
     */
    suspend fun processarRecorrentesVencidas(
        hoje: LocalDate = LocalDate.now(),
        autorCasa: String = "",
        autorCasaUid: String = ""
    ) {
        transacaoRecorrenteDao.obterVencidas(hoje).forEach { recorrente ->
            db.withTransaction {
                var proxima = recorrente.proximoLancamento
                // Lança todas as ocorrências pendentes (app pode ficar dias fechado)
                while (!proxima.isAfter(hoje)) {
                    transacaoDao.inserir(
                        Transacao(
                            valor = recorrente.valor,
                            tipo = recorrente.tipo,
                            categoria = recorrente.categoria,
                            descricao = recorrente.descricao,
                            data = proxima,
                            perfil = recorrente.perfil,
                            criadoPor = if (recorrente.perfil == Perfil.CASA) autorCasa else "",
                            criadoPorUid = if (recorrente.perfil == Perfil.CASA) {
                                autorCasaUid
                            } else {
                                ""
                            }
                        )
                    )
                    proxima = when (recorrente.frequencia) {
                        Frequencia.DIARIA -> proxima.plusDays(1)
                        Frequencia.SEMANAL -> proxima.plusWeeks(1)
                        Frequencia.MENSAL -> proxima.plusMonths(1)
                        Frequencia.ANUAL -> proxima.plusYears(1)
                    }
                }
                transacaoRecorrenteDao.atualizar(
                    recorrente.copy(proximoLancamento = proxima, atualizadoEm = agora())
                )
            }
        }
    }
}
