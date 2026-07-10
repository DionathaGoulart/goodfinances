package com.finapp.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.AparenciaManager
import com.finapp.data.PerfilManager
import com.finapp.data.SegurancaManager
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.ModoUso
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.TransacaoRecorrente
import android.app.PendingIntent
import com.finapp.data.io.BackupManager
import com.finapp.data.io.DadosImportados
import com.finapp.data.io.DriveBackupManager
import com.finapp.data.io.ExportManager
import com.finapp.data.io.ImportManager
import com.finapp.data.io.NotaFiscalManager
import com.finapp.data.notif.NotificacaoManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.SyncManager
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
import kotlinx.coroutines.flow.flowOf
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
    private val aparenciaManager: AparenciaManager,
    private val notaFiscalManager: NotaFiscalManager,
    private val segurancaManager: SegurancaManager,
    private val syncManager: SyncManager,
    private val driveBackupManager: DriveBackupManager,
    private val notificacaoManager: NotificacaoManager,
    private val casaManager: CasaManager
) : ViewModel() {

    // ---------- Notificações ----------

    val notificacoesAtivadas: StateFlow<Boolean> = notificacaoManager.ativado

    fun alternarNotificacoes(ativo: Boolean) {
        notificacaoManager.alternar(ativo)
        emitir(
            if (ativo) "Notificações ativadas — avisos de orçamento, DAS e recorrências"
            else "Notificações desativadas"
        )
    }

    // ---------- Sincronização entre aparelhos ----------

    val syncPessoalAtivado: StateFlow<Boolean> = syncManager.syncPessoalAtivado

    fun alternarSyncPessoal(ativo: Boolean) {
        syncManager.alternarSyncPessoal(ativo)
        emitir(
            if (ativo) "Sincronização ativada — seus dados sobem para a sua conta"
            else "Sincronização desativada"
        )
    }

    /** Espelhar meus lançamentos pessoais na visão Membros da casa. */
    val compartilharCasaAtivado: StateFlow<Boolean> = syncManager.compartilharCasaAtivado

    fun alternarCompartilharCasa(ativo: Boolean) {
        syncManager.alternarCompartilharCasa(ativo)
        emitir(
            if (ativo) "Compartilhando com a casa — aparece na visão Membros"
            else "Você não aparece mais na visão Membros"
        )
    }

    // ---------- Segurança ----------

    val bloqueioAtivado: StateFlow<Boolean> = segurancaManager.bloqueioAtivado

    fun alternarBloqueio(ativo: Boolean) {
        segurancaManager.alternarBloqueio(ativo)
        emitir(
            if (ativo) "Bloqueio ativado — será pedido ao abrir o app"
            else "Bloqueio desativado"
        )
    }

    // ---------- Aparência ----------

    val escalaFonte: StateFlow<EscalaFonte> = aparenciaManager.escalaFonte
    val corPessoal: StateFlow<CorApp> = aparenciaManager.corPessoal
    val corEmpresa: StateFlow<CorApp> = aparenciaManager.corEmpresa

    fun definirEscalaFonte(escala: EscalaFonte) = aparenciaManager.definirEscalaFonte(escala)

    fun definirCorPessoal(cor: CorApp) = aparenciaManager.definirCorPessoal(cor)

    fun definirCorEmpresa(cor: CorApp) = aparenciaManager.definirCorEmpresa(cor)

    /** Perfil escolhido pelo usuário (para o seletor de modo de uso). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilAtivo

    /** Tipo da empresa (MEI/CNPJ) — só relevante nos modos com empresa. */
    val tipoEmpresa: StateFlow<TipoEmpresa?> = perfilManager.tipoEmpresa

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

    /** Cartões de crédito do contexto ativo. */
    val cartoes: StateFlow<List<Cartao>> = perfilDados
        .flatMapLatest { repository.observarCartoes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Balde de empresa do MODO atual (MEI → aba Negócio, CNPJ → CNPJ),
     * independente da aba em que o usuário está. É onde o DAS mensal vive —
     * assim o campo aparece e grava certo mesmo com a Config aberta na aba
     * Pessoal. Null nos modos sem empresa.
     */
    val perfilEmpresa: StateFlow<Perfil?> = perfilManager.perfilAtivo
        .map { modo ->
            when (modo) {
                Perfil.MEI -> Perfil.MEI_NEGOCIO
                Perfil.CNPJ -> Perfil.CNPJ
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Valor do DAS mensal configurado (0 = não definido), em centavos. */
    val dasMensal: StateFlow<Long> = perfilEmpresa
        .flatMapLatest { empresa ->
            if (empresa == null) {
                flowOf(0L)
            } else {
                repository.observarRecorrentesAtivas(empresa).map { lista ->
                    lista.firstOrNull { it.descricao == DESCRICAO_DAS }?.valor ?: 0L
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun mudarPerfil(novo: Perfil) {
        perfilManager.mudarPerfil(novo)
        emitir("Modo alterado para ${novo.rotulo}")
    }

    fun mudarModo(modo: ModoUso) = mudarPerfil(modo.perfil)

    fun definirTipoEmpresa(tipo: TipoEmpresa) {
        perfilManager.definirTipoEmpresa(tipo)
        emitir("Empresa marcada como ${tipo.rotulo}")
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

        val proximo = proximoLancamentoMensal(configuracao.diaRecebimento, existente)

        if (existente == null) {
            repository.inserirRecorrente(
                TransacaoRecorrente(
                    valor = configuracao.salarioFixo,
                    tipo = TipoTransacao.GANHO,
                    categoria = "Salário",
                    descricao = DESCRICAO_SALARIO,
                    frequencia = Frequencia.MENSAL,
                    proximoLancamento = proximo,
                    diaMensal = configuracao.diaRecebimento,
                    perfil = perfil
                )
            )
        } else {
            repository.atualizarRecorrente(
                existente.copy(
                    valor = configuracao.salarioFixo,
                    proximoLancamento = proximo,
                    diaMensal = configuracao.diaRecebimento
                )
            )
        }
    }

    /**
     * Próxima data de lançamento de uma recorrência mensal no [dia] pedido.
     * Se a recorrência [existente] JÁ lançou hoje (o agendamento dela está no
     * futuro e o dia pedido é hoje), pula para o mês seguinte — reagendar
     * para hoje lançaria a mesma ocorrência de novo (salário/DAS duplicado).
     */
    private fun proximoLancamentoMensal(
        dia: Int,
        existente: TransacaoRecorrente?
    ): LocalDate {
        val hoje = LocalDate.now()
        // Dia 29-31 em mês curto cai no último dia (o diaMensal preserva a
        // intenção para os meses seguintes)
        val candidato = if (hoje.dayOfMonth <= dia) {
            hoje.withDayOfMonth(dia.coerceAtMost(hoje.lengthOfMonth()))
        } else {
            val proximoMes = hoje.plusMonths(1)
            proximoMes.withDayOfMonth(dia.coerceAtMost(proximoMes.lengthOfMonth()))
        }
        val jaLancouHoje = candidato == hoje &&
            existente != null &&
            existente.proximoLancamento.isAfter(hoje)
        return if (jaLancouHoje) candidato.plusMonths(1) else candidato
    }

    /**
     * [valorCentavos] em centavos. Materializa o DAS como despesa mensal
     * recorrente da empresa (categoria "Impostos", todo dia 20). Valor 0
     * encerra a recorrência. Grava sempre no balde de empresa do modo
     * atual, mesmo com a Config aberta na aba Pessoal.
     */
    fun atualizarDas(valorCentavos: Long) {
        if (valorCentavos < 0L) {
            emitir("Valor inválido")
            return
        }
        val perfil = perfilEmpresa.value ?: return
        viewModelScope.launch {
            runCatching {
                if (valorCentavos > 0L) {
                    repository.garantirCategoria(
                        perfil, CATEGORIA_DAS, TipoTransacao.GASTO, "#EF4444"
                    )
                }
                sincronizarRecorrenciaDas(perfil, valorCentavos)
            }
                .onSuccess {
                    emitir(
                        if (valorCentavos > 0L) "DAS salvo — lançado como despesa todo dia 20"
                        else "DAS mensal removido"
                    )
                }
                .onFailure { emitir("Erro ao salvar o DAS") }
        }
    }

    /** Cria/atualiza/encerra a recorrência mensal do DAS. */
    private suspend fun sincronizarRecorrenciaDas(perfil: Perfil, valorCentavos: Long) {
        val existente = repository.listarRecorrentesAtivas(perfil)
            .firstOrNull { it.descricao == DESCRICAO_DAS }

        if (valorCentavos <= 0L) {
            existente?.let { repository.atualizarRecorrente(it.copy(ativa = false)) }
            return
        }

        val proximo = proximoLancamentoMensal(DIA_DAS, existente)

        if (existente == null) {
            repository.inserirRecorrente(
                TransacaoRecorrente(
                    valor = valorCentavos,
                    tipo = TipoTransacao.GASTO,
                    categoria = CATEGORIA_DAS,
                    descricao = DESCRICAO_DAS,
                    frequencia = Frequencia.MENSAL,
                    proximoLancamento = proximo,
                    diaMensal = DIA_DAS,
                    perfil = perfil
                )
            )
        } else {
            repository.atualizarRecorrente(
                existente.copy(
                    valor = valorCentavos,
                    proximoLancamento = proximo,
                    diaMensal = DIA_DAS
                )
            )
        }
    }

    /**
     * Edita valor e (nas mensais) o dia de lançamento de uma recorrência.
     * Salário fixo e DAS são gerenciados pelos campos próprios da Config —
     * a UI não abre este fluxo para eles (manteria dois donos do mesmo dado).
     */
    fun editarRecorrente(recorrente: TransacaoRecorrente, valorCentavos: Long, dia: Int) {
        if (valorCentavos <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        val mensal = recorrente.frequencia == Frequencia.MENSAL
        if (mensal && dia !in 1..31) {
            emitir("Dia deve estar entre 1 e 31")
            return
        }
        viewModelScope.launch {
            runCatching {
                val atualizada = if (mensal && dia != recorrente.diaMensal) {
                    recorrente.copy(
                        valor = valorCentavos,
                        diaMensal = dia,
                        proximoLancamento = proximoLancamentoMensal(dia, recorrente)
                    )
                } else {
                    recorrente.copy(valor = valorCentavos)
                }
                repository.atualizarRecorrente(atualizada)
            }
                .onSuccess { emitir("Recorrência atualizada") }
                .onFailure { emitir("Erro ao atualizar recorrência") }
        }
    }

    /** True para recorrências gerenciadas pelos campos Salário/DAS da Config. */
    fun ehRecorrenteGerenciada(recorrente: TransacaoRecorrente): Boolean =
        recorrente.descricao == DESCRICAO_SALARIO || recorrente.descricao == DESCRICAO_DAS

    fun encerrarRecorrente(recorrente: TransacaoRecorrente) {
        viewModelScope.launch {
            runCatching { repository.atualizarRecorrente(recorrente.copy(ativa = false)) }
                .onSuccess { emitir("Recorrência encerrada") }
                .onFailure { emitir("Erro ao encerrar recorrência") }
        }
    }

    // ---------- Cartões de crédito ----------

    private fun diasValidos(fechamento: Int, vencimento: Int): Boolean =
        fechamento in 1..28 && vencimento in 1..28

    fun adicionarCartao(nome: String, diaFechamento: Int, diaVencimento: Int, cor: String) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome do cartão")
            return
        }
        if (!diasValidos(diaFechamento, diaVencimento)) {
            emitir("Dias de fechamento e vencimento devem estar entre 1 e 28")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.inserirCartao(
                    Cartao(
                        nome = nomeLimpo,
                        diaFechamento = diaFechamento,
                        diaVencimento = diaVencimento,
                        cor = cor,
                        perfil = perfilDados.value
                    )
                )
            }
                .onSuccess { emitir("Cartão adicionado") }
                .onFailure { emitir("Erro ao adicionar cartão") }
        }
    }

    fun editarCartao(
        cartao: Cartao,
        nome: String,
        diaFechamento: Int,
        diaVencimento: Int,
        cor: String
    ) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome do cartão")
            return
        }
        if (!diasValidos(diaFechamento, diaVencimento)) {
            emitir("Dias de fechamento e vencimento devem estar entre 1 e 28")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.atualizarCartao(
                    cartao.copy(
                        nome = nomeLimpo,
                        diaFechamento = diaFechamento,
                        diaVencimento = diaVencimento,
                        cor = cor
                    )
                )
            }
                .onSuccess { emitir("Cartão atualizado") }
                .onFailure { emitir("Erro ao atualizar cartão") }
        }
    }

    fun removerCartao(cartao: Cartao) {
        viewModelScope.launch {
            runCatching { repository.deletarCartao(cartao) }
                .onSuccess { emitir("Cartão removido") }
                .onFailure { emitir("Erro ao remover cartão") }
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

    /** Renomeia/recolore/reorça uma categoria, propagando o nome para o histórico. */
    fun editarCategoria(
        categoria: Categoria,
        novoNome: String,
        novaCor: String,
        novoOrcamentoCentavos: Long = categoria.orcamentoMensal
    ) {
        val nomeLimpo = novoNome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome da categoria")
            return
        }
        if (novoOrcamentoCentavos < 0L) {
            emitir("Orçamento inválido")
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
            runCatching {
                repository.renomearCategoria(
                    categoria, nomeLimpo, novaCor, novoOrcamentoCentavos,
                    autorUid = casaManager.usuario.value?.uid
                )
            }
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

    /**
     * ZIP do contexto ativo: CSV + notas fiscais organizadas por ano/mês
     * (para o imposto de renda / contador).
     */
    fun exportarZip(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val transacoes = repository.listarTransacoes(perfilDados.value)
                exportManager.exportarZip(uri, transacoes)
                transacoes.count { it.notaFiscal.isNotBlank() }
            }
                .onSuccess { emitir("ZIP exportado ($it notas fiscais)") }
                .onFailure { emitir(it.message ?: "Erro ao exportar ZIP") }
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

    // ---------- Backup das notas fiscais no Google Drive ----------

    val backupDriveAtivado: StateFlow<Boolean> = driveBackupManager.ativado

    /** Consentimento do Google pendente — a UI lança este PendingIntent. */
    private val _pedidoAutorizacaoDrive = MutableSharedFlow<PendingIntent>()
    val pedidoAutorizacaoDrive: SharedFlow<PendingIntent> = _pedidoAutorizacaoDrive

    /**
     * Liga/desliga o backup das notas no Drive. Ao ligar, pede o escopo:
     * se o Google exigir consentimento, emite o PendingIntent para a UI
     * e a ativação termina em [concluirAtivacaoDrive] (chamado no OK).
     */
    fun alternarBackupDrive(ativo: Boolean) {
        if (!ativo) {
            driveBackupManager.alternar(false)
            emitir("Backup das notas no Drive desativado")
            return
        }
        viewModelScope.launch {
            runCatching { driveBackupManager.pedirAutorizacao() }
                .onSuccess { pendente ->
                    if (pendente != null) {
                        _pedidoAutorizacaoDrive.emit(pendente)
                    } else {
                        concluirAtivacaoDrive()
                    }
                }
                .onFailure {
                    emitir("Não deu para conectar ao Google Drive — faça login e tente de novo")
                }
        }
    }

    /** Fecha a ativação (direto ou após o consentimento) e sobe as notas. */
    fun concluirAtivacaoDrive() {
        driveBackupManager.alternar(true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                driveBackupManager.sincronizarNotas(
                    repository.listarNotasFiscaisReferenciadas()
                )
            }
                .onSuccess { n ->
                    emitir(
                        if (n > 0) "Backup no Drive ativado ($n notas enviadas)"
                        else "Backup no Drive ativado"
                    )
                }
                .onFailure {
                    emitir("Drive ativado — as notas sobem no próximo backup")
                }
        }
    }

    /** Baixa do Drive as notas que não estão neste aparelho. */
    fun restaurarNotasDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { driveBackupManager.restaurarNotas() }
                .onSuccess { n ->
                    emitir(
                        if (n > 0) "$n notas restauradas do Drive"
                        else "Nenhuma nota nova para restaurar"
                    )
                }
                .onFailure { erro ->
                    emitir(
                        if (erro.message?.contains("não autorizado") == true) {
                            "Ative o backup no Drive primeiro"
                        } else {
                            "Erro ao restaurar do Drive — verifique a conexão"
                        }
                    )
                }
        }
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

    /**
     * Ação irreversível — a UI confirma antes com dialog.
     * [todosContextos] limpa todos os baldes locais do usuário (Pessoal,
     * os dois lados do modo misto e Empresa); a Casa é compartilhada e só
     * é limpa quando ELA é o contexto ativo. Depois, reconstrói o espelho
     * da visão Membros (remove inclusive órfãos de versões antigas).
     */
    fun limparTodosDados(todosContextos: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val baldes = if (todosContextos) {
                    Perfil.BALDES_DADOS - Perfil.CASA
                } else {
                    listOf(perfilDados.value)
                }
                val meuUid = casaManager.usuario.value?.uid
                baldes.forEach { balde ->
                    if (balde == Perfil.CASA && !meuUid.isNullOrBlank()) {
                        // Na Casa, apaga só os MEUS lançamentos (e as minhas
                        // notas): limpar tudo propagaria a deleção da carteira
                        // dos outros membros para todos os aparelhos.
                        repository.listarTransacoes(balde)
                            .filter { it.criadoPorUid == meuUid }
                            .forEach { notaFiscalManager.apagar(it.notaFiscal) }
                        repository.limparMinhasTransacoesCasa(meuUid)
                    } else {
                        // Apaga também os arquivos de nota fiscal do balde
                        repository.listarTransacoes(balde)
                            .forEach { notaFiscalManager.apagar(it.notaFiscal) }
                        repository.deletarTodasTransacoes(balde)
                    }
                    repository.deletarTodasMetas(balde)
                    repository.deletarTodasContas(balde)
                }
                // Feed da visão Membros: zera e deixa o push re-espelhar
                // só o que sobrou (sem isso, órfãos antigos ficam para sempre)
                runCatching { syncManager.ressincronizarEspelho() }
            }
                .onSuccess {
                    emitir(
                        if (todosContextos) "Todos os contextos deste aparelho foram limpos"
                        else "Todos os dados do contexto foram apagados"
                    )
                }
                .onFailure { emitir("Erro ao apagar dados") }
        }
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }

    private companion object {
        /** Marca a recorrência gerenciada automaticamente pela configuração de salário. */
        const val DESCRICAO_SALARIO = "Salário fixo"

        /** Marca a recorrência gerenciada pela configuração do DAS mensal. */
        const val DESCRICAO_DAS = "DAS mensal"
        const val CATEGORIA_DAS = "Impostos"
        const val DIA_DAS = 20
    }
}
