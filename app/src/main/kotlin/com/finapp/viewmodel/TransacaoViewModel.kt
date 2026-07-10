package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Cartao
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /** Abas/contextos disponíveis (Pessoal/Empresa/Casa) — destino da transferência. */
    val contextos: StateFlow<List<Perfil>> = perfilManager.contextosDisponiveis

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Última transação deletada, para o "desfazer" do snackbar. */
    private var ultimaDeletada: Transacao? = null

    /** Categorias ativas por tipo — alimentam o dropdown do modal. */
    val categoriasGanho: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GANHO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoriasGasto: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GASTO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cartões de crédito do perfil — alimentam o seletor do modal. */
    val cartoes: StateFlow<List<Cartao>> = perfil
        .flatMapLatest { repository.observarCartoes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- CRUD ----------

    /**
     * [valorCentavos] em centavos e é o valor TOTAL da compra. Se
     * [repetirMensalmente], cria também uma recorrência mensal a partir do
     * mês seguinte. [parcelas] > 1 divide o total em lançamentos mensais
     * iguais (o resto da divisão vai na primeira parcela); a nota fiscal
     * fica só na primeira. [lancarProLaborePessoal] (modo misto, aba
     * Empresa): espelha o gasto como ganho no balde Pessoal — o pró-labore
     * do dono.
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
        lancarProLaborePessoal: Boolean = false,
        cartao: Cartao? = null
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
            // O usuário digita o TOTAL; cada parcela leva total/n e o resto
            // da divisão inteira vai na primeira (a soma bate com o total).
            val valorParcela = valorCentavos / totalParcelas
            val valorPrimeira = valorCentavos - valorParcela * (totalParcelas - 1)
            // Compra no crédito: a 1ª parcela cai no vencimento da fatura da
            // compra; as demais, nas faturas dos meses seguintes.
            val vencimentoBase = cartao?.let { repository.vencimentoFatura(it, data) }
            runCatching {
                repeat(totalParcelas) { indice ->
                    val descricaoFinal = if (totalParcelas > 1) {
                        val base = descricao.trim().ifBlank { categoria }
                        "$base (${indice + 1}/$totalParcelas)"
                    } else {
                        descricao.trim()
                    }
                    val dataLancamento = if (vencimentoBase != null) {
                        vencimentoBase.plusMonths(indice.toLong())
                    } else {
                        data.plusMonths(indice.toLong())
                    }
                    repository.inserirTransacao(
                        Transacao(
                            valor = if (indice == 0) valorPrimeira else valorParcela,
                            tipo = tipo,
                            categoria = categoria,
                            descricao = descricaoFinal,
                            data = dataLancamento,
                            perfil = perfil.value,
                            criadoPor = autor,
                            criadoPorUid = autorUid,
                            notaFiscal = if (indice == 0) notaFiscal else "",
                            cartaoUuid = cartao?.uuid.orEmpty(),
                            dataCompra = if (cartao != null) data else null,
                            // Crédito: pendente até pagar a fatura. Parcelas
                            // futuras sem cartão também aguardam pagamento.
                            pago = cartao == null && indice == 0
                        )
                    )
                }
                if (repetirMensalmente && totalParcelas == 1 && cartao == null) {
                    repository.inserirRecorrente(
                        TransacaoRecorrente(
                            valor = valorCentavos,
                            tipo = tipo,
                            categoria = categoria,
                            descricao = descricao.trim(),
                            frequencia = Frequencia.MENSAL,
                            proximoLancamento = data.plusMonths(1),
                            // Dia da transação original: plusMonths pode ter
                            // truncado (31/01 -> 28/02) e perderia a intenção
                            diaMensal = data.dayOfMonth,
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
                            cartao != null && totalParcelas > 1 ->
                                "Compra no crédito em ${totalParcelas}x — cai nas faturas"
                            cartao != null ->
                                "Compra no crédito registrada — cai na fatura"
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

    /** Vencimento previsto da fatura de uma compra no crédito (prévia no modal). */
    fun previsaoVencimento(cartao: Cartao, dataCompra: LocalDate): LocalDate =
        repository.vencimentoFatura(cartao, dataCompra)

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
            runCatching {
                if (transacao.transferenciaId.isNotBlank()) {
                    // Perna de transferência: espelha valor/data na outra
                    // perna — editar um lado só dessincronizaria os contextos
                    repository.atualizarTransferencia(transacao)
                } else {
                    repository.atualizarTransacao(transacao)
                }
            }
                .onSuccess {
                    emitir(
                        if (transacao.transferenciaId.isNotBlank()) {
                            "Transferência atualizada nos dois contextos"
                        } else {
                            "Transação atualizada"
                        }
                    )
                }
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
