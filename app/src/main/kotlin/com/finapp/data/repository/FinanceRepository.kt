package com.finapp.data.repository

import com.finapp.data.CategoriasPadrao
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.ConfiguracaoPerfilDao
import com.finapp.data.db.SomaPorCategoria
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.TransacaoRecorrenteDao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponto único de acesso aos dados financeiros.
 * ViewModels nunca falam com os DAOs diretamente.
 */
@Singleton
class FinanceRepository @Inject constructor(
    private val transacaoDao: TransacaoDao,
    private val categoriaDao: CategoriaDao,
    private val configuracaoPerfilDao: ConfiguracaoPerfilDao,
    private val transacaoRecorrenteDao: TransacaoRecorrenteDao
) {

    // ---------- Transações ----------

    suspend fun inserirTransacao(transacao: Transacao): Long = transacaoDao.inserir(transacao)

    suspend fun atualizarTransacao(transacao: Transacao) = transacaoDao.atualizar(transacao)

    suspend fun deletarTransacao(transacao: Transacao) = transacaoDao.deletar(transacao)

    suspend fun deletarTodasTransacoes(perfil: Perfil) = transacaoDao.deletarTodas(perfil)

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

    suspend fun atualizarCategoria(categoria: Categoria) = categoriaDao.atualizar(categoria)

    suspend fun deletarCategoria(categoria: Categoria) = categoriaDao.deletar(categoria)

    /** Cria as categorias padrão na primeira vez que o perfil é usado. */
    suspend fun garantirCategoriasPadrao(perfil: Perfil) {
        if (categoriaDao.contar(perfil) == 0) {
            categoriaDao.inserirTodas(CategoriasPadrao.para(perfil))
        }
    }

    /**
     * Renomeia/recolore uma categoria e propaga o novo nome para o
     * histórico de transações e para as recorrências.
     */
    suspend fun renomearCategoria(categoria: Categoria, novoNome: String, novaCor: String) {
        categoriaDao.atualizar(categoria.copy(nome = novoNome, cor = novaCor))
        if (novoNome != categoria.nome) {
            transacaoDao.renomearCategoria(categoria.perfil, categoria.nome, novoNome)
            transacaoRecorrenteDao.renomearCategoria(categoria.perfil, categoria.nome, novoNome)
        }
    }

    // ---------- Importação / Backup ----------

    suspend fun listarTransacoes(perfil: Perfil): List<Transacao> =
        transacaoDao.listarTodas(perfil)

    suspend fun listarCategorias(perfil: Perfil): List<Categoria> =
        categoriaDao.listarTodas(perfil)

    /**
     * Importa transações e categorias para o perfil.
     * [substituir] apaga as transações atuais antes; caso contrário mescla,
     * ignorando duplicatas (mesma data + valor + categoria).
     * Retorna quantas transações foram de fato inseridas.
     */
    suspend fun importarDados(
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        perfil: Perfil,
        substituir: Boolean
    ): Int {
        if (substituir) transacaoDao.deletarTodas(perfil)

        // Categorias: insere apenas as que ainda não existem (nome + tipo)
        val categoriasExistentes = categoriaDao.listarTodas(perfil)
        val categoriasNovas = categorias.filter { nova ->
            categoriasExistentes.none {
                it.nome.equals(nova.nome, ignoreCase = true) && it.tipo == nova.tipo
            }
        }.map { it.copy(id = 0, perfil = perfil) }
        if (categoriasNovas.isNotEmpty()) categoriaDao.inserirTodas(categoriasNovas)

        // Transações: deduplicação por data + valor + categoria
        val existentes = if (substituir) emptyList() else transacaoDao.listarTodas(perfil)
        val chavesExistentes = existentes
            .map { Triple(it.data, it.valor, it.categoria) }
            .toHashSet()
        val novas = transacoes
            .filter { Triple(it.data, it.valor, it.categoria) !in chavesExistentes }
            .map { it.copy(id = 0, perfil = perfil) }
        if (novas.isNotEmpty()) transacaoDao.inserirTodas(novas)
        return novas.size
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
        transacaoRecorrenteDao.atualizar(recorrente)

    suspend fun deletarRecorrente(recorrente: TransacaoRecorrente) =
        transacaoRecorrenteDao.deletar(recorrente)

    /**
     * Lança as transações recorrentes vencidas e reagenda cada uma
     * conforme a frequência. Chamar ao abrir o app.
     */
    suspend fun processarRecorrentesVencidas(hoje: LocalDate = LocalDate.now()) {
        transacaoRecorrenteDao.obterVencidas(hoje).forEach { recorrente ->
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
                        perfil = recorrente.perfil
                    )
                )
                proxima = when (recorrente.frequencia) {
                    Frequencia.DIARIA -> proxima.plusDays(1)
                    Frequencia.SEMANAL -> proxima.plusWeeks(1)
                    Frequencia.MENSAL -> proxima.plusMonths(1)
                    Frequencia.ANUAL -> proxima.plusYears(1)
                }
            }
            transacaoRecorrenteDao.atualizar(recorrente.copy(proximoLancamento = proxima))
        }
    }
}
