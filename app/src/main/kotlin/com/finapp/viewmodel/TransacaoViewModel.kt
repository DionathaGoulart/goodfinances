package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import android.net.Uri
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente
import com.finapp.data.db.entities.podeSerEditadaPor
import com.finapp.data.io.NotaFiscalManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.utils.Intervalo
import com.finapp.utils.PeriodoFiltro
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransacaoViewModel @Inject constructor(
    private val repository: FinanceRepository,
    perfilManager: PerfilManager,
    private val casaManager: CasaManager,
    private val notaFiscalManager: NotaFiscalManager
) : ViewModel() {

    /** Balde de dados efetivo (no MEI, acompanha a aba Pessoal/Negócio). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilDados

    private val _filtro = MutableStateFlow(PeriodoFiltro.MES)
    val filtro: StateFlow<PeriodoFiltro> = _filtro.asStateFlow()

    private val _periodoCustom = MutableStateFlow<Intervalo?>(null)
    val periodoCustom: StateFlow<Intervalo?> = _periodoCustom.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    /** Filtro por tipo (null = ganhos e gastos). */
    private val _filtroTipo = MutableStateFlow<TipoTransacao?>(null)
    val filtroTipo: StateFlow<TipoTransacao?> = _filtroTipo.asStateFlow()

    /** Filtro por categoria (null = todas). */
    private val _filtroCategoria = MutableStateFlow<String?>(null)
    val filtroCategoria: StateFlow<String?> = _filtroCategoria.asStateFlow()

    /** Filtro por membro da Casa (null = todos; só se aplica no perfil CASA). */
    private val _filtroMembro = MutableStateFlow<String?>(null)
    val filtroMembro: StateFlow<String?> = _filtroMembro.asStateFlow()

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Última transação deletada, para o "desfazer" do swipe. */
    private var ultimaDeletada: Transacao? = null

    /** Data atual — re-emite à meia-noite para o filtro nunca ficar velho. */
    private val dataAtual = fluxoDataAtual()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /**
     * Lista reativa: refaz a query quando perfil/filtro/período/dia mudam
     * e aplica a busca por descrição em memória.
     * (sem o filtro de membro — os chips de membro derivam desta lista)
     */
    private val listaFiltrada =
        combine(perfil, _filtro, _periodoCustom, dataAtual) { p, f, custom, hoje ->
            p to (f.intervalo(hoje) ?: custom)
        }
            .flatMapLatest { (p, intervalo) ->
                if (intervalo != null) {
                    repository.observarTransacoesPeriodo(p, intervalo.inicio, intervalo.fim)
                } else {
                    repository.observarTransacoes(p)
                }
            }
            .combine(_busca) { lista, termo ->
                if (termo.isBlank()) lista
                else lista.filter { it.descricao.contains(termo, ignoreCase = true) }
            }
            .combine(_filtroTipo) { lista, tipo ->
                if (tipo == null) lista else lista.filter { it.tipo == tipo }
            }
            .combine(_filtroCategoria) { lista, categoria ->
                if (categoria == null) lista
                else lista.filter { it.categoria.equals(categoria, ignoreCase = true) }
            }

    val transacoes: StateFlow<List<Transacao>> =
        combine(listaFiltrada, _filtroMembro, perfil) { lista, membro, p ->
            if (p != Perfil.CASA || membro == null) lista
            else lista.filter { it.criadoPor == membro }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nomes para os chips de filtro por membro (só no perfil Casa). */
    val membrosCasa: StateFlow<List<String>> =
        combine(perfil, listaFiltrada) { p, lista ->
            if (p != Perfil.CASA) {
                emptyList()
            } else {
                lista.map { it.criadoPor }.filter { it.isNotBlank() }.distinct().sorted()
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categorias ativas por tipo — alimentam o dropdown do modal. */
    val categoriasGanho: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GANHO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoriasGasto: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GASTO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Filtros e busca ----------

    fun filtrar(filtro: PeriodoFiltro) {
        _filtro.value = filtro
    }

    fun definirPeriodoCustom(inicio: LocalDate, fim: LocalDate) {
        _periodoCustom.value = Intervalo(inicio, fim)
        _filtro.value = PeriodoFiltro.PERSONALIZADO
    }

    fun buscar(termo: String) {
        _busca.value = termo
    }

    /** null limpa o filtro de tipo. */
    fun filtrarTipo(tipo: TipoTransacao?) {
        _filtroTipo.value = tipo
    }

    /** null limpa o filtro de categoria. */
    fun filtrarCategoria(categoria: String?) {
        _filtroCategoria.value = categoria
    }

    /** null limpa o filtro de membro (perfil Casa). */
    fun filtrarMembro(membro: String?) {
        _filtroMembro.value = membro
    }

    // ---------- CRUD ----------

    /**
     * [valorCentavos] em centavos. Se [repetirMensalmente], cria também uma
     * recorrência mensal a partir do mês seguinte.
     * [parcelas] > 1 cria um lançamento por mês com o valor informado
     * (valor da parcela); a nota fiscal fica só na primeira.
     * [lancarProLaborePessoal] (modo misto, aba Empresa): espelha o gasto
     * como ganho no balde Pessoal — o pró-labore do dono.
     */
    fun adicionarTransacao(
        valorCentavos: Long,
        tipo: TipoTransacao,
        categoria: String,
        descricao: String,
        data: LocalDate,
        repetirMensalmente: Boolean = false,
        notaFiscal: String = "",
        parcelas: Int = 1,
        lancarProLaborePessoal: Boolean = false
    ) {
        if (valorCentavos <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        viewModelScope.launch {
            // No perfil Casa, registra quem lançou (aparece para os outros membros)
            val usuario = casaManager.usuario.value.takeIf { perfil.value == Perfil.CASA }
            val autor = usuario?.nome.orEmpty()
            val autorUid = usuario?.uid.orEmpty()
            val totalParcelas = parcelas.coerceIn(1, 24)
            runCatching {
                repeat(totalParcelas) { indice ->
                    val descricaoFinal = if (totalParcelas > 1) {
                        val base = descricao.trim().ifBlank { categoria }
                        "$base (${indice + 1}/$totalParcelas)"
                    } else {
                        descricao.trim()
                    }
                    repository.inserirTransacao(
                        Transacao(
                            valor = valorCentavos,
                            tipo = tipo,
                            categoria = categoria,
                            descricao = descricaoFinal,
                            data = data.plusMonths(indice.toLong()),
                            perfil = perfil.value,
                            criadoPor = autor,
                            criadoPorUid = autorUid,
                            notaFiscal = if (indice == 0) notaFiscal else ""
                        )
                    )
                }
                if (repetirMensalmente && totalParcelas == 1) {
                    repository.inserirRecorrente(
                        TransacaoRecorrente(
                            valor = valorCentavos,
                            tipo = tipo,
                            categoria = categoria,
                            descricao = descricao.trim(),
                            frequencia = Frequencia.MENSAL,
                            proximoLancamento = data.plusMonths(1),
                            perfil = perfil.value
                        )
                    )
                }
                if (lancarProLaborePessoal &&
                    perfil.value == Perfil.MEI_NEGOCIO &&
                    tipo == TipoTransacao.GASTO &&
                    totalParcelas == 1
                ) {
                    lancarProLabore(valorCentavos, descricao.trim(), data)
                }
            }
                .onSuccess {
                    emitir(
                        when {
                            totalParcelas > 1 ->
                                "Compra parcelada em ${totalParcelas}x adicionada"
                            lancarProLaborePessoal ->
                                "Gasto lançado e pró-labore adicionado no Pessoal"
                            repetirMensalmente -> "Transação adicionada (repete todo mês)"
                            else -> "Transação adicionada"
                        }
                    )
                }
                .onFailure { emitir("Erro ao adicionar transação") }
        }
    }

    /**
     * Transferência entre contextos (Pessoal / Empresa / Casa): registra a
     * saída na origem (contexto atual) e a entrada no destino, ambas na
     * categoria "Transferência", com descrições cruzadas como histórico.
     */
    fun transferir(
        destino: Perfil,
        valorCentavos: Long,
        descricao: String,
        data: LocalDate = LocalDate.now()
    ) {
        val origem = perfil.value
        if (valorCentavos <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        if (destino == origem) {
            emitir("Escolha um destino diferente da origem")
            return
        }
        viewModelScope.launch {
            val nomeAutor = casaManager.usuario.value?.nome.orEmpty()
            val uidAutor = casaManager.usuario.value?.uid.orEmpty()
            val detalhe = descricao.trim()
            // Mesmo id nas duas pernas: deletar uma deleta a outra
            val vinculo = UUID.randomUUID().toString()
            runCatching {
                repository.garantirCategoriaTransferencia(origem, destino)
                repository.inserirTransacao(
                    Transacao(
                        valor = valorCentavos,
                        tipo = TipoTransacao.GASTO,
                        categoria = FinanceRepository.NOME_TRANSFERENCIA,
                        descricao = juntar("Para ${destino.rotulo}", detalhe),
                        data = data,
                        perfil = origem,
                        criadoPor = if (origem == Perfil.CASA) nomeAutor else "",
                        criadoPorUid = if (origem == Perfil.CASA) uidAutor else "",
                        transferenciaId = vinculo
                    )
                )
                repository.inserirTransacao(
                    Transacao(
                        valor = valorCentavos,
                        tipo = TipoTransacao.GANHO,
                        categoria = FinanceRepository.NOME_TRANSFERENCIA,
                        descricao = juntar("De ${origem.rotulo}", detalhe),
                        data = data,
                        perfil = destino,
                        criadoPor = if (destino == Perfil.CASA) nomeAutor else "",
                        criadoPorUid = if (destino == Perfil.CASA) uidAutor else "",
                        transferenciaId = vinculo
                    )
                )
            }
                .onSuccess {
                    emitir("Transferido de ${origem.rotulo} para ${destino.rotulo}")
                }
                .onFailure { emitir("Erro ao transferir") }
        }
    }

    private fun juntar(base: String, detalhe: String): String =
        if (detalhe.isBlank()) base else "$base — $detalhe"

    /** Espelho do pró-labore no balde Pessoal do modo misto. */
    private suspend fun lancarProLabore(
        valorCentavos: Long,
        descricao: String,
        data: LocalDate
    ) {
        val categoriasGanhoPessoal = repository
            .observarCategoriasAtivas(Perfil.MEI_PESSOAL, TipoTransacao.GANHO)
            .first()
        val categoria = categoriasGanhoPessoal.firstOrNull { it.nome == "Salário" }?.nome
            ?: categoriasGanhoPessoal.firstOrNull()?.nome
            ?: "Outros"
        repository.inserirTransacao(
            Transacao(
                valor = valorCentavos,
                tipo = TipoTransacao.GANHO,
                categoria = categoria,
                descricao = if (descricao.isBlank()) "Pró-labore" else "Pró-labore — $descricao",
                data = data,
                perfil = Perfil.MEI_PESSOAL
            )
        )
    }

    /** Na Casa, só o autor do lançamento pode editar/apagar. */
    fun podeEditar(transacao: Transacao): Boolean =
        transacao.podeSerEditadaPor(
            uid = casaManager.usuario.value?.uid,
            nomeUsuario = casaManager.usuario.value?.nome
        )

    fun editarTransacao(transacao: Transacao) {
        if (!podeEditar(transacao)) {
            emitir("Só quem lançou pode editar esta transação")
            return
        }
        if (transacao.valor <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        viewModelScope.launch {
            runCatching { repository.atualizarTransacao(transacao) }
                .onSuccess { emitir("Transação atualizada") }
                .onFailure { emitir("Erro ao atualizar transação") }
        }
    }

    fun deletarTransacao(transacao: Transacao) {
        if (!podeEditar(transacao)) {
            emitir("Só quem lançou pode apagar esta transação")
            return
        }
        viewModelScope.launch {
            runCatching { repository.deletarTransacao(transacao) }
                .onSuccess { ultimaDeletada = transacao }
                .onFailure { emitir("Erro ao deletar transação") }
        }
    }

    fun desfazerDelete() {
        val transacao = ultimaDeletada ?: return
        ultimaDeletada = null
        viewModelScope.launch {
            // Deleção é lógica: restaurar = limpar o tombstone
            runCatching { repository.restaurarTransacao(transacao) }
                .onFailure { emitir("Erro ao restaurar transação") }
        }
    }

    // ---------- Nota fiscal / comprovante (todos os contextos) ----------

    /** Copia o arquivo escolhido para o app e retorna o nome (usado pelo modal). */
    suspend fun anexarNota(uri: Uri): String = notaFiscalManager.anexar(uri)

    /** Destino de uma foto da câmera: (nome do arquivo, uri para o TakePicture). */
    fun criarDestinoFoto(): Pair<String, Uri> = notaFiscalManager.criarDestinoFoto()

    /** Converte a foto tirada pela câmera em PDF; retorna o nome final. */
    suspend fun converterFotoParaPdf(nome: String): String =
        notaFiscalManager.converterImagemParaPdf(nome)

    fun abrirNota(nome: String) {
        runCatching { notaFiscalManager.abrir(nome) }
            .onFailure { emitir("Nenhum app no aparelho consegue abrir a nota") }
    }

    fun apagarNota(nome: String) = notaFiscalManager.apagar(nome)

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }
}
