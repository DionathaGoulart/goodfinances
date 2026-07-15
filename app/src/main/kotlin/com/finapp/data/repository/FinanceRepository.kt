package com.finapp.data.repository

import androidx.room.withTransaction
import com.finapp.data.CategoriasPadrao
import com.finapp.data.db.AppDatabase
import com.finapp.data.db.CartaoDao
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.ConfiguracaoPerfilDao
import com.finapp.data.db.ContaAgendadaDao
import com.finapp.data.db.MetaDao
import com.finapp.data.db.SomaPorCategoria
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.TransacaoRecorrenteDao
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Meta
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
    private val cartaoDao: CartaoDao,
    private val metaDao: MetaDao,
    private val contaAgendadaDao: ContaAgendadaDao,
    private val perfilManager: com.finapp.data.PerfilManager
) {

    private fun agora() = System.currentTimeMillis()

    // ---------- Transações ----------

    suspend fun inserirTransacao(transacao: Transacao): Long = transacaoDao.inserir(transacao)

    suspend fun atualizarTransacao(transacao: Transacao) =
        transacaoDao.atualizar(transacao.copy(atualizadoEm = agora()))

    /**
     * Edita uma perna de transferência espelhando VALOR e DATA na outra —
     * as duas pernas precisam continuar batendo (mesmo montante saindo de um
     * contexto e entrando no outro). Descrição/nota são de cada perna.
     */
    suspend fun atualizarTransferencia(transacao: Transacao) {
        val momento = agora()
        transacaoDao.atualizar(transacao.copy(atualizadoEm = momento))
        if (transacao.transferenciaId.isBlank()) return
        transacaoDao.listarPorTransferencia(transacao.transferenciaId)
            .filter { it.uuid != transacao.uuid && !it.deletado }
            .forEach {
                transacaoDao.atualizar(
                    it.copy(valor = transacao.valor, data = transacao.data, atualizadoEm = momento)
                )
            }
    }

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

    /** Marca/desmarca como paga: pendente não conta no saldo até pagar. */
    suspend fun marcarTransacaoPaga(transacao: Transacao, pago: Boolean) =
        transacaoDao.atualizar(transacao.copy(pago = pago, atualizadoEm = agora()))

    /** Paga a fatura: marca todas as compras do grupo como pagas de uma vez. */
    suspend fun pagarTransacoes(transacoes: List<Transacao>) {
        if (transacoes.isEmpty()) return
        transacaoDao.marcarPagas(transacoes.map { it.uuid }, pago = true, agora = agora())
    }

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
     * "Limpar dados" no contexto Casa: tombstona só os MEUS lançamentos.
     * Limpar tudo propagaria a deleção dos lançamentos dos outros membros
     * para todos os aparelhos — um membro apagaria a carteira dos demais.
     */
    suspend fun limparMinhasTransacoesCasa(uid: String) =
        transacaoDao.marcarMinhasDeletadas(Perfil.CASA, uid, agora())

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
        metaDao.deletarTodas(Perfil.CASA)
        contaAgendadaDao.deletarTodas(Perfil.CASA)
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

    /** Sugestão de categoria pelo histórico de descrições parecidas. */
    suspend fun sugerirCategoria(perfil: Perfil, tipo: TipoTransacao, descricao: String): String? =
        transacaoDao.categoriaMaisUsada(perfil, tipo, descricao.trim())

    // ---------- Agregações (Dashboard e Análise) ----------

    fun observarSaldoTotal(perfil: Perfil): Flow<Long> =
        transacaoDao.observarSaldoTotal(perfil)

    /**
     * TOTAL da compra parcelada a que uma parcela "base (i/N)" pertence
     * (soma das N parcelas vivas). Informativo na edição da parcela.
     */
    suspend fun somarCompraParcelada(
        perfil: Perfil,
        cartaoUuid: String,
        descricaoBase: String,
        totalParcelas: Int
    ): Long {
        val baseEscapada = descricaoBase
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return transacaoDao.somarParcelasIrmas(
            perfil, cartaoUuid, "$baseEscapada (%/$totalParcelas)"
        )
    }

    /** Pendências do período por tipo: GASTO = a pagar, GANHO = a receber. */
    fun observarPendentePorTipo(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<Long> = transacaoDao.observarPendentePorTipo(perfil, tipo, inicio, fim)

    /** Gastos atrasados (pendência com vencimento passado), em centavos. */
    fun observarAtrasado(perfil: Perfil, hoje: LocalDate): Flow<Long> =
        transacaoDao.observarAtrasado(perfil, hoje)

    /** Pendências de GASTO vencendo até [ate] — lembretes de vencimento. */
    suspend fun listarGastosPendentesAte(perfil: Perfil, ate: LocalDate): List<Transacao> =
        transacaoDao.listarGastosPendentesAte(perfil, ate)

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
        novoOrcamento: Long = categoria.orcamentoMensal,
        autorUid: String? = null
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
            // Na Casa, a categoria é coletiva e propaga a todos, mas as
            // transações são por autor: re-carimbo só as minhas (senão as dos
            // outros ficam com nome novo só neste aparelho e travam o sync).
            if (categoria.perfil == Perfil.CASA && !autorUid.isNullOrBlank()) {
                transacaoDao.renomearCategoriaDoAutor(
                    categoria.perfil, categoria.nome, novoNome, momento, autorUid
                )
            } else {
                transacaoDao.renomearCategoria(categoria.perfil, categoria.nome, novoNome, momento)
            }
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

    suspend fun inserirCartao(cartao: Cartao): Long {
        val id = cartaoDao.inserir(cartao)
        sincronizarEspelhoCartaoNaCasa(cartao)
        return id
    }

    suspend fun atualizarCartao(cartao: Cartao) {
        val atualizado = cartao.copy(atualizadoEm = agora())
        cartaoDao.atualizar(atualizado)
        sincronizarEspelhoCartaoNaCasa(atualizado)
    }

    /** Deleção lógica (tombstone) — tombstona junto o espelho na Casa. */
    suspend fun deletarCartao(cartao: Cartao) {
        val deletado = cartao.copy(deletado = true, atualizadoEm = agora())
        cartaoDao.atualizar(deletado)
        sincronizarEspelhoCartaoNaCasa(deletado)
    }

    /**
     * Upsert do espelho na Casa de um cartão pessoal (one-way: editar/deletar
     * o original propaga para o espelho; mexer no espelho NÃO volta para o
     * original — na Casa ele é read-only). O uuid determinístico faz os
     * aparelhos da mesma conta convergirem para a mesma linha, e o sync da
     * Casa (balde CASA) leva o espelho aos outros membros sozinho.
     * Empresa (CNPJ/MEI_NEGOCIO) fica de fora por construção.
     */
    private suspend fun sincronizarEspelhoCartaoNaCasa(original: Cartao) {
        if (original.perfil !in Perfil.BALDES_PESSOAIS) return
        if (!perfilManager.temCasa()) return
        val uuidEspelho = uuidEspelhoCartao(original.uuid)
        val existente = cartaoDao.obterPorUuid(uuidEspelho)
        val espelho = Cartao(
            id = existente?.id ?: 0,
            uuid = uuidEspelho,
            nome = original.nome,
            diaFechamento = original.diaFechamento,
            diaVencimento = original.diaVencimento,
            cor = original.cor,
            perfil = Perfil.CASA,
            origemUuid = original.uuid,
            atualizadoEm = agora(),
            deletado = original.deletado
        )
        if (existente == null) cartaoDao.inserir(espelho) else cartaoDao.atualizar(espelho)
    }

    /**
     * Reconciliação: espelha na Casa todos os cartões pessoais vivos. Rodada
     * ao entrar/criar/carregar uma casa — cobre cartões criados antes da
     * feature (ou antes de ter casa) e os que chegaram pelo sync pessoal
     * direto no DAO (que não passa por [inserirCartao]).
     */
    suspend fun espelharCartoesPessoaisNaCasa() {
        Perfil.BALDES_PESSOAIS.forEach { balde ->
            cartaoDao.listarTodos(balde).forEach { sincronizarEspelhoCartaoNaCasa(it) }
        }
    }

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

    suspend fun inserirRecorrente(recorrente: TransacaoRecorrente): Long {
        // Garante o dia desejado da recorrência mensal (ver [TransacaoRecorrente.diaMensal])
        var pronta = if (
            recorrente.frequencia == Frequencia.MENSAL && recorrente.diaMensal == 0
        ) {
            recorrente.copy(diaMensal = recorrente.proximoLancamento.dayOfMonth)
        } else {
            recorrente
        }
        // GANHO mensal (salário, ganho repetido): o cursor de auto-recebimento
        // nasce junto — cobre todos os caminhos de criação num lugar só
        if (pronta.frequencia == Frequencia.MENSAL &&
            pronta.tipo == TipoTransacao.GANHO &&
            pronta.proximaConfirmacao == null
        ) {
            pronta = pronta.copy(proximaConfirmacao = pronta.proximoLancamento)
        }
        return transacaoRecorrenteDao.inserir(pronta)
    }

    suspend fun atualizarRecorrente(recorrente: TransacaoRecorrente) =
        transacaoRecorrenteDao.atualizar(recorrente.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarRecorrente(recorrente: TransacaoRecorrente) =
        transacaoRecorrenteDao.atualizar(
            recorrente.copy(deletado = true, atualizadoEm = agora())
        )

    companion object {
        /** Categoria automática das transferências entre contextos. */
        const val NOME_TRANSFERENCIA = "Transferência"

        /**
         * Uuid determinístico do espelho na Casa de um cartão pessoal:
         * todos os aparelhos derivam o mesmo uuid e o sync converge (LWW)
         * em vez de duplicar.
         */
        fun uuidEspelhoCartao(uuidOriginal: String): String = java.util.UUID
            .nameUUIDFromBytes("cartao-casa-$uuidOriginal".toByteArray())
            .toString()

        /**
         * Uuid determinístico da ocorrência de uma recorrência numa data:
         * reprocessar nunca duplica (o insert checa por uuid antes) e um
         * tombstone antigo bloqueia a ressurreição da ocorrência apagada.
         */
        fun uuidOcorrenciaRecorrente(uuidRecorrente: String, data: LocalDate): String =
            java.util.UUID
                .nameUUIDFromBytes(
                    "recorrencia-$uuidRecorrente-${data.toEpochDay()}".toByteArray()
                )
                .toString()
    }

    /**
     * Materializa as ocorrências das recorrências (mensais até
     * [MESES_HORIZONTE_MENSAL] meses à frente — a Home mostra as pendências
     * ao navegar os meses futuros) e auto-confirma o GANHO mensal vencido
     * (salário "cai" sozinho no dia). Chamar ao abrir o app.
     *
     * [autorCasa]/[autorCasaUid]: autoria carimbada nos lançamentos gerados
     * no balde CASA (sem isso qualquer membro poderia editá-los).
     * Cada recorrência roda numa transação do Room: lançar as ocorrências e
     * reagendar é atômico — cancelamento no meio não duplica lançamentos; o
     * uuid determinístico por (recorrência, data) blinda contra reprocesso.
     */
    suspend fun processarRecorrentesVencidas(
        hoje: LocalDate = LocalDate.now(),
        autorCasa: String = "",
        autorCasaUid: String = ""
    ) {
        val horizonte = YearMonth.from(hoje)
            .plusMonths(MESES_HORIZONTE_MENSAL)
            .atEndOfMonth()
        transacaoRecorrenteDao.obterVencidas(horizonte, hoje).forEach { recorrente ->
            db.withTransaction {
                // Cursor de auto-recebimento: backfill ANTES de materializar
                // (nascesse depois, já estaria 12 meses à frente e pularia
                // as confirmações reais)
                var confirmacao = recorrente.proximaConfirmacao
                if (confirmacao == null &&
                    recorrente.frequencia == Frequencia.MENSAL &&
                    recorrente.tipo == TipoTransacao.GANHO
                ) {
                    confirmacao = recorrente.proximoLancamento
                }

                // Materializa as ocorrências dentro do horizonte
                var proxima = recorrente.proximoLancamento
                while (deveLancarRecorrente(recorrente, proxima, hoje)) {
                    val uuidOcorrencia = uuidOcorrenciaRecorrente(recorrente.uuid, proxima)
                    if (transacaoDao.obterPorUuid(uuidOcorrencia) == null) {
                        transacaoDao.inserir(
                            Transacao(
                                uuid = uuidOcorrencia,
                                valor = recorrente.valor,
                                tipo = recorrente.tipo,
                                categoria = recorrente.categoria,
                                descricao = recorrente.descricao,
                                data = proxima,
                                perfil = recorrente.perfil,
                                criadoPor = if (recorrente.perfil == Perfil.CASA) {
                                    autorCasa
                                } else {
                                    ""
                                },
                                criadoPorUid = if (recorrente.perfil == Perfil.CASA) {
                                    autorCasaUid
                                } else {
                                    ""
                                },
                                recorrenciaUuid = recorrente.uuid,
                                // MENSAL nasce pendente ("a pagar"/"a receber",
                                // como fatura); nas demais frequências o GANHO
                                // entra recebido no próprio dia
                                pago = if (recorrente.frequencia == Frequencia.MENSAL) {
                                    false
                                } else {
                                    recorrente.tipo == TipoTransacao.GANHO
                                }
                            )
                        )
                    }
                    proxima = proximaOcorrencia(
                        recorrente.frequencia, recorrente.diaMensal, proxima
                    )
                }

                // Auto-recebimento do GANHO mensal: confirma cada ocorrência
                // cujo dia já chegou, UMA vez (desmarcar não é re-marcado)
                while (confirmacao != null && !confirmacao.isAfter(hoje)) {
                    transacaoDao.confirmarOcorrencia(recorrente.uuid, confirmacao, agora())
                    confirmacao = proximaOcorrencia(
                        Frequencia.MENSAL, recorrente.diaMensal, confirmacao
                    )
                }

                // Passou do "dura até": a recorrência se encerra sozinha
                val terminou = recorrente.terminaEm != null &&
                    proxima.isAfter(recorrente.terminaEm)

                // Só regrava se algo mudou (não re-carimbar atualizadoEm à toa)
                if (proxima != recorrente.proximoLancamento ||
                    confirmacao != recorrente.proximaConfirmacao ||
                    terminou
                ) {
                    transacaoRecorrenteDao.atualizar(
                        recorrente.copy(
                            proximoLancamento = proxima,
                            proximaConfirmacao = confirmacao,
                            ativa = recorrente.ativa && !terminou,
                            atualizadoEm = agora()
                        )
                    )
                }
            }
        }
    }

    /**
     * Edita a recorrência e propaga IN-PLACE para as ocorrências futuras
     * NÃO PAGAS vinculadas: novo valor; se o [novoDia] mudou, reancora a
     * data no mesmo mês. NUNCA reancora o cursor para trás (as ocorrências
     * já materializadas cobrem os próximos meses). Reduzir [novoTerminaEm]
     * tombstona as ocorrências além do novo limite.
     */
    suspend fun atualizarRecorrenteComOcorrencias(
        recorrente: TransacaoRecorrente,
        novoValor: Long,
        novoDia: Int = recorrente.diaMensal,
        novoTerminaEm: LocalDate? = recorrente.terminaEm,
        hoje: LocalDate = LocalDate.now()
    ) {
        db.withTransaction {
            val momento = agora()
            val mensal = recorrente.frequencia == Frequencia.MENSAL
            val diaMudou = mensal && novoDia in 1..31 && novoDia != recorrente.diaMensal
            transacaoDao.listarOcorrenciasPendentes(recorrente.uuid, hoje).forEach { ocorrencia ->
                transacaoDao.atualizar(
                    ocorrencia.copy(
                        valor = novoValor,
                        data = if (diaMudou) {
                            ajustarDiaNoMes(ocorrencia.data, novoDia)
                        } else {
                            ocorrencia.data
                        },
                        atualizadoEm = momento
                    )
                )
            }
            val terminaAntigo = recorrente.terminaEm
            if (novoTerminaEm != null &&
                (terminaAntigo == null || novoTerminaEm < terminaAntigo)
            ) {
                transacaoDao.tombstonarOcorrenciasApos(recorrente.uuid, novoTerminaEm, momento)
            }
            transacaoRecorrenteDao.atualizar(
                recorrente.copy(
                    valor = novoValor,
                    diaMensal = if (diaMudou) novoDia else recorrente.diaMensal,
                    terminaEm = novoTerminaEm,
                    proximoLancamento = if (diaMudou) {
                        ajustarDiaNoMes(recorrente.proximoLancamento, novoDia)
                    } else {
                        recorrente.proximoLancamento
                    },
                    proximaConfirmacao = if (diaMudou) {
                        recorrente.proximaConfirmacao?.let { ajustarDiaNoMes(it, novoDia) }
                    } else {
                        recorrente.proximaConfirmacao
                    },
                    atualizadoEm = momento
                )
            )
        }
    }

    /** Encerra a recorrência e tombstona as pendências futuras vinculadas. */
    suspend fun encerrarRecorrenteComOcorrencias(
        recorrente: TransacaoRecorrente,
        hoje: LocalDate = LocalDate.now()
    ) {
        db.withTransaction {
            val momento = agora()
            // aposDe exclusivo: ontem inclui as pendências de hoje em diante
            transacaoDao.tombstonarOcorrenciasApos(
                recorrente.uuid, hoje.minusDays(1), momento
            )
            transacaoRecorrenteDao.atualizar(
                recorrente.copy(ativa = false, atualizadoEm = momento)
            )
        }
    }

    // ---------- Metas de economia ----------

    fun observarMetas(perfil: Perfil): Flow<List<Meta>> = metaDao.observarTodas(perfil)

    suspend fun inserirMeta(meta: Meta): Long = metaDao.inserir(meta)

    suspend fun atualizarMeta(meta: Meta) =
        metaDao.atualizar(meta.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarMeta(meta: Meta) =
        metaDao.atualizar(meta.copy(deletado = true, atualizadoEm = agora()))

    /** Limpeza: tombstona todas as metas do balde (propaga no sync). */
    suspend fun deletarTodasMetas(perfil: Perfil) =
        metaDao.marcarTodasDeletadas(perfil, agora())

    /** Metas vivas do perfil — backup. */
    suspend fun listarMetas(perfil: Perfil): List<Meta> = metaDao.listarTodas(perfil)

    /** Restaura metas de um backup (mescla por uuid: só insere as que faltam). */
    suspend fun importarMetas(perfil: Perfil, metas: List<Meta>): Int {
        var inseridas = 0
        metas.forEach { meta ->
            if (metaDao.obterPorUuid(meta.uuid) == null) {
                metaDao.inserir(meta.copy(id = 0, perfil = perfil))
                inseridas++
            }
        }
        return inseridas
    }

    /**
     * Aporta (ou retira, com [delta] negativo) na meta. O guardado nunca fica
     * negativo. Não mexe no saldo das transações — é um acompanhamento à parte.
     */
    suspend fun aportarMeta(meta: Meta, delta: Long) {
        val novo = (meta.valorGuardado + delta).coerceAtLeast(0L)
        metaDao.atualizar(meta.copy(valorGuardado = novo, atualizadoEm = agora()))
    }

    // ---------- Contas a pagar/receber agendadas ----------

    fun observarContasPendentes(perfil: Perfil): Flow<List<ContaAgendada>> =
        contaAgendadaDao.observarPendentes(perfil)

    suspend fun listarContasPendentesAte(perfil: Perfil, ate: LocalDate): List<ContaAgendada> =
        contaAgendadaDao.listarPendentesAte(perfil, ate)

    suspend fun inserirConta(conta: ContaAgendada): Long = contaAgendadaDao.inserir(conta)

    suspend fun atualizarConta(conta: ContaAgendada) =
        contaAgendadaDao.atualizar(conta.copy(atualizadoEm = agora()))

    /** Deleção lógica (tombstone). */
    suspend fun deletarConta(conta: ContaAgendada) =
        contaAgendadaDao.atualizar(conta.copy(deletado = true, atualizadoEm = agora()))

    /** Limpeza: tombstona todas as contas do balde (propaga no sync). */
    suspend fun deletarTodasContas(perfil: Perfil) =
        contaAgendadaDao.marcarTodasDeletadas(perfil, agora())

    /** Contas vivas do perfil (pendentes e pagas) — backup. */
    suspend fun listarContas(perfil: Perfil): List<ContaAgendada> =
        contaAgendadaDao.listarTodas(perfil)

    /** Restaura contas de um backup (mescla por uuid: só insere as que faltam). */
    suspend fun importarContas(perfil: Perfil, contas: List<ContaAgendada>): Int {
        var inseridas = 0
        contas.forEach { conta ->
            if (contaAgendadaDao.obterPorUuid(conta.uuid) == null) {
                contaAgendadaDao.inserir(conta.copy(id = 0, perfil = perfil))
                inseridas++
            }
        }
        return inseridas
    }

    /**
     * Marca a conta como paga: cria a [Transacao] correspondente na data de
     * pagamento ([dataPagamento], padrão hoje) e marca a conta como paga.
     * Atômico — cancelar no meio não deixa transação sem baixa.
     */
    suspend fun pagarConta(conta: ContaAgendada, dataPagamento: LocalDate = LocalDate.now()) {
        db.withTransaction {
            // Idempotente: na Casa, dois membros podem pagar a mesma conta
            // antes do sync propagar o `pago` — o segundo não pode duplicar
            // a despesa. Relê o estado dentro da transação e usa um uuid
            // DETERMINÍSTICO derivado da conta: mesmo que dois aparelhos
            // paguem offline, o sync converge para uma única transação.
            val atual = contaAgendadaDao.obterPorUuid(conta.uuid) ?: conta
            if (atual.pago) return@withTransaction
            val uuidPagamento = java.util.UUID
                .nameUUIDFromBytes("pagamento-${conta.uuid}".toByteArray())
                .toString()
            if (transacaoDao.obterPorUuid(uuidPagamento) == null) {
                transacaoDao.inserir(
                    Transacao(
                        uuid = uuidPagamento,
                        valor = conta.valor,
                        tipo = conta.tipo,
                        categoria = conta.categoria,
                        descricao = conta.descricao,
                        data = dataPagamento,
                        perfil = conta.perfil
                    )
                )
            }
            contaAgendadaDao.atualizar(atual.copy(pago = true, atualizadoEm = agora()))
        }
    }
}
