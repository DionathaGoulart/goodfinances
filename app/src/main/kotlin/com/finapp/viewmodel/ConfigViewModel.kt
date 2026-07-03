package com.finapp.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.AparenciaManager
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.TransacaoRecorrente
import com.finapp.data.io.BackupManager
import com.finapp.data.io.DadosImportados
import com.finapp.data.io.ExportManager
import com.finapp.data.io.ImportManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.utils.CorApp
import com.finapp.utils.EscalaFonte
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager,
    private val exportManager: ExportManager,
    private val importManager: ImportManager,
    private val backupManager: BackupManager,
    private val aparenciaManager: AparenciaManager
) : ViewModel() {

    // ---------- Aparência ----------

    val escalaFonte: StateFlow<EscalaFonte> = aparenciaManager.escalaFonte
    val corPrimaria: StateFlow<CorApp> = aparenciaManager.corPrimaria

    fun definirEscalaFonte(escala: EscalaFonte) = aparenciaManager.definirEscalaFonte(escala)

    fun definirCorPrimaria(cor: CorApp) = aparenciaManager.definirCorPrimaria(cor)

    /** Perfil escolhido pelo usuário (para o seletor de perfil). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilAtivo

    /** Balde de dados efetivo (no MEI, acompanha a aba Pessoal/Negócio). */
    private val perfilDados: StateFlow<Perfil> = perfilManager.perfilDados

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Configuração do perfil ativo (salário fixo, dia de recebimento). */
    val configuracao: StateFlow<ConfiguracaoPerfil> = perfilDados
        .flatMapLatest { p ->
            repository.observarConfiguracao(p).map { it ?: ConfiguracaoPerfil(perfil = p) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ConfiguracaoPerfil(perfil = Perfil.PESSOA_FISICA)
        )

    val categorias: StateFlow<List<Categoria>> = perfilDados
        .flatMapLatest { repository.observarCategorias(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recorrências ativas do perfil (inclui a do salário fixo, se houver). */
    val recorrentes: StateFlow<List<TransacaoRecorrente>> = perfilDados
        .flatMapLatest { repository.observarRecorrentesAtivas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun mudarPerfil(novo: Perfil) {
        perfilManager.mudarPerfil(novo)
        emitir("Perfil alterado para ${novo.rotulo}")
    }

    /** [salarioCentavos] em centavos. Mantém uma recorrência mensal de ganho em sincronia. */
    fun atualizarSalario(salarioCentavos: Long, diaRecebimento: Int) {
        if (salarioCentavos < 0L) {
            emitir("Salário inválido")
            return
        }
        if (diaRecebimento !in 1..28) {
            emitir("Dia de recebimento deve estar entre 1 e 28")
            return
        }
        viewModelScope.launch {
            val configuracao = ConfiguracaoPerfil(
                perfil = perfilDados.value,
                salarioFixo = salarioCentavos,
                diaRecebimento = diaRecebimento
            )
            runCatching {
                repository.salvarConfiguracao(configuracao)
                sincronizarRecorrenciaSalario(configuracao)
            }
                .onSuccess {
                    emitir(
                        if (salarioCentavos > 0L) {
                            "Salário salvo — será lançado todo dia $diaRecebimento"
                        } else {
                            "Configuração salva"
                        }
                    )
                }
                .onFailure { emitir("Erro ao salvar configuração") }
        }
    }

    /**
     * Cria/atualiza a recorrência mensal do salário (categoria "Salário").
     * Salário zerado encerra a recorrência.
     */
    private suspend fun sincronizarRecorrenciaSalario(configuracao: ConfiguracaoPerfil) {
        val perfil = perfilDados.value
        val existente = repository.listarRecorrentesAtivas(perfil)
            .firstOrNull { it.descricao == DESCRICAO_SALARIO }

        if (configuracao.salarioFixo <= 0L) {
            existente?.let { repository.atualizarRecorrente(it.copy(ativa = false)) }
            return
        }

        val hoje = LocalDate.now()
        val proximo = if (hoje.dayOfMonth <= configuracao.diaRecebimento) {
            hoje.withDayOfMonth(configuracao.diaRecebimento)
        } else {
            hoje.plusMonths(1).withDayOfMonth(configuracao.diaRecebimento)
        }

        if (existente == null) {
            repository.inserirRecorrente(
                TransacaoRecorrente(
                    valor = configuracao.salarioFixo,
                    tipo = TipoTransacao.GANHO,
                    categoria = "Salário",
                    descricao = DESCRICAO_SALARIO,
                    frequencia = Frequencia.MENSAL,
                    proximoLancamento = proximo,
                    perfil = perfil
                )
            )
        } else {
            repository.atualizarRecorrente(
                existente.copy(
                    valor = configuracao.salarioFixo,
                    proximoLancamento = proximo
                )
            )
        }
    }

    fun encerrarRecorrente(recorrente: TransacaoRecorrente) {
        viewModelScope.launch {
            runCatching { repository.atualizarRecorrente(recorrente.copy(ativa = false)) }
                .onSuccess { emitir("Recorrência encerrada") }
                .onFailure { emitir("Erro ao encerrar recorrência") }
        }
    }

    fun adicionarCategoria(nome: String, tipo: TipoTransacao, cor: String) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome da categoria")
            return
        }
        viewModelScope.launch {
            val jaExiste = categorias.first().any {
                it.nome.equals(nomeLimpo, ignoreCase = true) && it.tipo == tipo
            }
            if (jaExiste) {
                emitir("Categoria \"$nomeLimpo\" já existe")
                return@launch
            }
            runCatching {
                repository.inserirCategoria(
                    Categoria(nome = nomeLimpo, tipo = tipo, cor = cor, perfil = perfilDados.value)
                )
            }
                .onSuccess { emitir("Categoria adicionada") }
                .onFailure { emitir("Erro ao adicionar categoria") }
        }
    }

    /** Renomeia/recolore uma categoria, propagando o nome para o histórico. */
    fun editarCategoria(categoria: Categoria, novoNome: String, novaCor: String) {
        val nomeLimpo = novoNome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome da categoria")
            return
        }
        viewModelScope.launch {
            val duplicada = categorias.first().any {
                it.id != categoria.id &&
                    it.nome.equals(nomeLimpo, ignoreCase = true) &&
                    it.tipo == categoria.tipo
            }
            if (duplicada) {
                emitir("Categoria \"$nomeLimpo\" já existe")
                return@launch
            }
            runCatching { repository.renomearCategoria(categoria, nomeLimpo, novaCor) }
                .onSuccess { emitir("Categoria atualizada") }
                .onFailure { emitir("Erro ao atualizar categoria") }
        }
    }

    /** Arquiva em vez de deletar, para não órfãos no histórico de transações. */
    fun arquivarCategoria(categoria: Categoria) {
        viewModelScope.launch {
            runCatching { repository.atualizarCategoria(categoria.copy(arquivada = true)) }
                .onSuccess { emitir("Categoria arquivada") }
                .onFailure { emitir("Erro ao arquivar categoria") }
        }
    }

    fun reativarCategoria(categoria: Categoria) {
        viewModelScope.launch {
            runCatching { repository.atualizarCategoria(categoria.copy(arquivada = false)) }
                .onSuccess { emitir("Categoria reativada") }
                .onFailure { emitir("Erro ao reativar categoria") }
        }
    }

    // ---------- Export / Import / Backup ----------

    /** Backup automático semanal (Configurações > Dados). */
    val backupAtivado: StateFlow<Boolean> = backupManager.ativado

    /** Prévia do arquivo escolhido para importar — abre o dialog Mesclar/Substituir. */
    private val _previaImportacao = MutableStateFlow<DadosImportados?>(null)
    val previaImportacao: StateFlow<DadosImportados?> = _previaImportacao.asStateFlow()

    fun exportarCsv(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val transacoes = repository.listarTransacoes(perfilDados.value)
                exportManager.exportarCsv(uri, transacoes)
                transacoes.size
            }
                .onSuccess { emitir("CSV exportado ($it transações)") }
                .onFailure { emitir("Erro ao exportar CSV") }
        }
    }

    fun exportarJson(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val perfil = perfilDados.value
                val transacoes = repository.listarTransacoes(perfil)
                exportManager.exportarJson(
                    uri, perfil, transacoes, repository.listarCategorias(perfil)
                )
                transacoes.size
            }
                .onSuccess { emitir("JSON exportado ($it transações)") }
                .onFailure { emitir("Erro ao exportar JSON") }
        }
    }

    fun exportarPdf(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val perfil = perfilDados.value
                exportManager.exportarPdf(uri, perfil, repository.listarTransacoes(perfil))
            }
                .onSuccess { emitir("Relatório PDF exportado") }
                .onFailure { emitir("Erro ao exportar PDF") }
        }
    }

    /** Lê e valida o arquivo; se ok, preenche a prévia para o dialog. */
    fun prepararImportacao(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { importManager.ler(uri, perfilDados.value) }
                .onSuccess { _previaImportacao.value = it }
                .onFailure { emitir("Arquivo inválido: ${it.message}") }
        }
    }

    fun confirmarImportacao(substituir: Boolean) {
        val previa = _previaImportacao.value ?: return
        _previaImportacao.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.importarDados(
                    transacoes = previa.transacoes,
                    categorias = previa.categorias,
                    perfil = perfilDados.value,
                    substituir = substituir
                )
            }
                .onSuccess { emitir("$it transações importadas") }
                .onFailure { emitir("Erro ao importar dados") }
        }
    }

    fun cancelarImportacao() {
        _previaImportacao.value = null
    }

    fun alternarBackupAutomatico(ativo: Boolean) {
        backupManager.alternar(ativo)
        if (ativo) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { backupManager.executarAgora() }
                    .onSuccess { n ->
                        emitir(
                            if (n > 0) "Backup automático ativado ($n perfis salvos)"
                            else "Backup automático ativado"
                        )
                    }
                    .onFailure { emitir("Erro ao criar backup") }
            }
        }
    }

    fun restaurarBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backupManager.restaurarUltimo() }
                .onSuccess { emitir("$it transações restauradas do backup") }
                .onFailure { emitir(it.message ?: "Erro ao restaurar backup") }
        }
    }

    /** Ação irreversível — a UI deve confirmar antes com dialog. */
    fun limparTodosDados() {
        viewModelScope.launch {
            runCatching { repository.deletarTodasTransacoes(perfilDados.value) }
                .onSuccess { emitir("Todos os dados do perfil foram apagados") }
                .onFailure { emitir("Erro ao apagar dados") }
        }
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }

    private companion object {
        /** Marca a recorrência gerenciada automaticamente pela configuração de salário. */
        const val DESCRICAO_SALARIO = "Salário fixo"
    }
}
