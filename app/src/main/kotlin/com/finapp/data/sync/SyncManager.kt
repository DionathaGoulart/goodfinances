package com.finapp.data.sync

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.db.CartaoDao
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.ContaAgendadaDao
import com.finapp.data.db.MetaDao
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.TransacaoRecorrenteDao
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza transações e categorias com o Firestore:
 *
 * - CASA (sempre que o usuário está numa casa):
 *   `casas/{id}/transacoes|categorias/{uuid}` — compartilhado entre membros.
 * - PESSOAL (opt-in em Configurações, logado com Google): todos os baldes
 *   locais (Pessoal, Empresa, os dois lados do modo misto) em
 *   `usuarios/{uid}/perfis/{perfil}/transacoes|categorias/{uuid}` — mesmos
 *   dados em todos os aparelhos da conta.
 *
 * Mecânica comum:
 * - PULL: snapshot listeners aplicam docs remotos no Room quando o
 *   `atualizadoEm` remoto é mais novo ("última edição vence").
 * - PUSH: observa a última modificação local e sobe (com debounce) as
 *   linhas alteradas desde a marca por destino. Escritas offline ficam na
 *   fila durável do Firestore, então o push é fire-and-forget.
 * - Deleções viajam como tombstones (`deletado = true`), nunca como
 *   remoção de documento.
 * - Notas fiscais NÃO sincronizam (o arquivo é local): o pull preserva o
 *   `notaFiscal` que já existir no aparelho.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val casaManager: CasaManager,
    private val transacaoDao: TransacaoDao,
    private val categoriaDao: CategoriaDao,
    private val cartaoDao: CartaoDao,
    private val metaDao: MetaDao,
    private val contaAgendadaDao: ContaAgendadaDao,
    private val transacaoRecorrenteDao: TransacaoRecorrenteDao
) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Relógio local — teto da marca de push (protege contra skew remoto). */
    private fun agora() = System.currentTimeMillis()

    /**
     * Aplica um doc remoto tolerando falha pontual (ex: dois snapshots
     * concorrentes inserindo o mesmo uuid violam o índice único) — um doc
     * ruim não pode derrubar o app nem abortar o restante do lote.
     */
    private suspend fun aplicarComProtecao(bloco: suspend () -> Unit) {
        runCatching { bloco() }
            .onFailure { android.util.Log.w("SyncManager", "Falha ao aplicar doc remoto", it) }
    }

    private val trabalhosCasa = mutableListOf<Job>()
    private val listenersCasa = mutableListOf<ListenerRegistration>()
    private val trabalhosPessoal = mutableListOf<Job>()
    private val listenersPessoal = mutableListOf<ListenerRegistration>()
    private val trabalhosEspelho = mutableListOf<Job>()
    private val listenersEspelho = mutableListOf<ListenerRegistration>()

    /** Baldes sincronizados no modo pessoal (a Casa tem fluxo próprio). */
    private val baldesPessoais = Perfil.BALDES_DADOS - Perfil.CASA

    private val _syncPessoalAtivado =
        MutableStateFlow(prefs.getBoolean(CHAVE_SYNC_PESSOAL, false))
    /** Sync pessoal entre aparelhos (opt-in nas Configurações). */
    val syncPessoalAtivado: StateFlow<Boolean> = _syncPessoalAtivado.asStateFlow()

    fun alternarSyncPessoal(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_SYNC_PESSOAL, ativo) }
        _syncPessoalAtivado.value = ativo
    }

    private val _compartilharCasaAtivado =
        MutableStateFlow(prefs.getBoolean(CHAVE_COMPARTILHAR_CASA, false))
    /** Espelhar meus lançamentos PESSOAIS na visão Membros da casa (opt-in). */
    val compartilharCasaAtivado: StateFlow<Boolean> = _compartilharCasaAtivado.asStateFlow()

    fun alternarCompartilharCasa(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_COMPARTILHAR_CASA, ativo) }
        _compartilharCasaAtivado.value = ativo
        // Desligou: some da visão dos outros membros e zera as marcas
        // (se religar, re-espelha tudo)
        if (!ativo) {
            escopo.launch { runCatching { apagarEspelhoRemoto() } }
        }
    }

    /** Chamado uma vez, no Application. Liga/desliga conforme casa e login. */
    fun iniciar() {
        escopo.launch {
            casaManager.carregarCasa()
            casaManager.casa.collect { casa ->
                parar(listenersCasa, trabalhosCasa)
                if (casa != null) iniciarSyncCasa(casa.id)
            }
        }
        escopo.launch {
            combine(casaManager.usuario, _syncPessoalAtivado) { usuario, ativo ->
                if (ativo) usuario?.uid else null
            }.collect { uid ->
                parar(listenersPessoal, trabalhosPessoal)
                if (uid != null) iniciarSyncPessoal(uid)
            }
        }
        escopo.launch {
            combine(
                casaManager.casa,
                casaManager.usuario,
                _compartilharCasaAtivado
            ) { casa, usuario, compartilhar -> Triple(casa, usuario, compartilhar) }
                .collect { (casa, usuario, compartilhar) ->
                    parar(listenersEspelho, trabalhosEspelho)
                    if (casa != null && usuario != null) {
                        iniciarEspelhoCasa(casa, usuario, compartilhar)
                    }
                }
        }
    }

    /**
     * Visão "Membros" da casa: puxa os lançamentos pessoais espelhados dos
     * OUTROS membros para o balde local CASA_MEMBROS e, se [compartilhar],
     * espelha os meus baldes pessoais (nunca os de empresa) para a casa.
     */
    private fun iniciarEspelhoCasa(casa: Casa, usuario: UsuarioCasa, compartilhar: Boolean) {
        // PULL: feed de cada outro membro (REMOVED = ele parou de compartilhar)
        casa.membros.filter { it != usuario.uid }.forEach { membroUid ->
            listenersEspelho += espelhoRef(casa.id, membroUid)
                .addSnapshotListener { snapshot, erro ->
                    if (erro != null || snapshot == null) return@addSnapshotListener
                    val mudancas = snapshot.documentChanges
                    if (mudancas.isNotEmpty()) {
                        escopo.launch {
                            mudancas.forEach { mudanca ->
                                aplicarComProtecao {
                                    if (mudanca.type == DocumentChange.Type.REMOVED) {
                                        transacaoDao.deletarPorUuidEPerfil(
                                            mudanca.document.id, Perfil.CASA_MEMBROS
                                        )
                                    } else {
                                        aplicarTransacaoRemota(
                                            mudanca.document, Perfil.CASA_MEMBROS
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }

        // PUSH: meus lançamentos pessoais, com meu nome como autor.
        // O espelho é um feed de exibição: deleção vira REMOÇÃO do doc
        // (não tombstone), para o feed não acumular lixo.
        if (compartilhar) {
            Perfil.BALDES_PESSOAIS.forEach { balde ->
                trabalhosEspelho += escopo.launch {
                    transacaoDao.observarUltimaModificacao(balde)
                        .debounce(1_500)
                        .collect {
                            runCatching {
                                empurrarTransacoes(
                                    ref = espelhoRef(casa.id, usuario.uid),
                                    perfil = balde,
                                    chaveMarca = chaveMarcaEspelho(casa.id, balde),
                                    autorPadrao = usuario.nome,
                                    deletarTombstones = true
                                )
                            }
                        }
                }
            }
        }
    }

    /**
     * Reconstrói o espelho na visão Membros: apaga TODOS os meus docs no
     * feed remoto (inclusive órfãos de versões antigas, que apagavam sem
     * tombstone), zera as marcas e re-espelha na hora o que existe de
     * verdade. Chamado pelo "Limpar Todos os Dados".
     */
    suspend fun ressincronizarEspelho() {
        val casa = casaManager.casa.value ?: return
        val usuario = casaManager.usuario.value ?: return
        apagarEspelhoRemoto()
        if (_compartilharCasaAtivado.value) {
            Perfil.BALDES_PESSOAIS.forEach { balde ->
                runCatching {
                    empurrarTransacoes(
                        ref = espelhoRef(casa.id, usuario.uid),
                        perfil = balde,
                        chaveMarca = chaveMarcaEspelho(casa.id, balde),
                        autorPadrao = usuario.nome,
                        deletarTombstones = true
                    )
                }
            }
        }
    }

    /** Apaga meu espelho na casa (parei de compartilhar) e zera as marcas. */
    private suspend fun apagarEspelhoRemoto() {
        val casa = casaManager.casa.value ?: return
        val uid = casaManager.usuario.value?.uid ?: return
        val ref = espelhoRef(casa.id, uid)
        while (true) {
            val docs = ref.limit(400).get().await().documents
            if (docs.isEmpty()) break
            val batch = db.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        prefs.edit {
            Perfil.BALDES_PESSOAIS.forEach { remove(chaveMarcaEspelho(casa.id, it)) }
        }
    }

    private fun chaveMarcaEspelho(casaId: String, balde: Perfil) =
        "sync_marca_espelho_${casaId}_${balde.name}"

    private fun espelhoRef(casaId: String, uid: String): CollectionReference =
        db.collection("casas").document(casaId)
            .collection("membros").document(uid)
            .collection("transacoes")

    private fun iniciarSyncCasa(casaId: String) {
        ligarSync(
            transacoes = transacoesCasaRef(casaId),
            categorias = categoriasCasaRef(casaId),
            perfil = Perfil.CASA,
            // Chaves antigas preservadas para não re-empurrar tudo
            chaveMarcaTransacoes = "sync_marca_transacoes_$casaId",
            chaveMarcaCategorias = "sync_marca_categorias_$casaId",
            listeners = listenersCasa,
            trabalhos = trabalhosCasa
        )
        ligarSyncCartoes(
            cartoes = cartoesCasaRef(casaId),
            perfil = Perfil.CASA,
            chaveMarca = "sync_marca_cartoes_$casaId",
            listeners = listenersCasa,
            trabalhos = trabalhosCasa
        )
        ligarSyncMetas(
            metas = metasCasaRef(casaId),
            perfil = Perfil.CASA,
            chaveMarca = "sync_marca_metas_$casaId",
            listeners = listenersCasa,
            trabalhos = trabalhosCasa
        )
        ligarSyncContas(
            contas = contasCasaRef(casaId),
            perfil = Perfil.CASA,
            chaveMarca = "sync_marca_contas_$casaId",
            listeners = listenersCasa,
            trabalhos = trabalhosCasa
        )
    }

    private fun iniciarSyncPessoal(uid: String) {
        baldesPessoais.forEach { perfil ->
            ligarSync(
                transacoes = transacoesPessoalRef(uid, perfil),
                categorias = categoriasPessoalRef(uid, perfil),
                perfil = perfil,
                chaveMarcaTransacoes = "sync_marca_t_${uid}_${perfil.name}",
                chaveMarcaCategorias = "sync_marca_c_${uid}_${perfil.name}",
                listeners = listenersPessoal,
                trabalhos = trabalhosPessoal
            )
            ligarSyncCartoes(
                cartoes = cartoesPessoalRef(uid, perfil),
                perfil = perfil,
                chaveMarca = "sync_marca_cart_${uid}_${perfil.name}",
                listeners = listenersPessoal,
                trabalhos = trabalhosPessoal
            )
            ligarSyncMetas(
                metas = metasPessoalRef(uid, perfil),
                perfil = perfil,
                chaveMarca = "sync_marca_meta_${uid}_${perfil.name}",
                listeners = listenersPessoal,
                trabalhos = trabalhosPessoal
            )
            ligarSyncContas(
                contas = contasPessoalRef(uid, perfil),
                perfil = perfil,
                chaveMarca = "sync_marca_conta_${uid}_${perfil.name}",
                listeners = listenersPessoal,
                trabalhos = trabalhosPessoal
            )
        }
    }

    /** PULL em tempo real + PUSH reativo dos cartões de um balde. */
    private fun ligarSyncCartoes(
        cartoes: CollectionReference,
        perfil: Perfil,
        chaveMarca: String,
        listeners: MutableList<ListenerRegistration>,
        trabalhos: MutableList<Job>
    ) {
        listeners += cartoes.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarComProtecao { aplicarCartaoRemoto(it, perfil) } } }
            }
        }
        trabalhos += escopo.launch {
            cartaoDao.observarUltimaModificacao(perfil)
                .debounce(1_500)
                .collect {
                    runCatching { empurrarCartoes(cartoes, perfil, chaveMarca) }
                }
        }
    }

    /** PULL em tempo real + PUSH reativo das metas de um balde. */
    private fun ligarSyncMetas(
        metas: CollectionReference,
        perfil: Perfil,
        chaveMarca: String,
        listeners: MutableList<ListenerRegistration>,
        trabalhos: MutableList<Job>
    ) {
        listeners += metas.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarComProtecao { aplicarMetaRemota(it, perfil) } } }
            }
        }
        trabalhos += escopo.launch {
            metaDao.observarUltimaModificacao(perfil)
                .debounce(1_500)
                .collect { runCatching { empurrarMetas(metas, perfil, chaveMarca) } }
        }
    }

    /** PULL em tempo real + PUSH reativo das contas agendadas de um balde. */
    private fun ligarSyncContas(
        contas: CollectionReference,
        perfil: Perfil,
        chaveMarca: String,
        listeners: MutableList<ListenerRegistration>,
        trabalhos: MutableList<Job>
    ) {
        listeners += contas.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarComProtecao { aplicarContaRemota(it, perfil) } } }
            }
        }
        trabalhos += escopo.launch {
            contaAgendadaDao.observarUltimaModificacao(perfil)
                .debounce(1_500)
                .collect { runCatching { empurrarContas(contas, perfil, chaveMarca) } }
        }
    }

    /** PULL em tempo real + PUSH reativo de um par de coleções para um balde. */
    private fun ligarSync(
        transacoes: CollectionReference,
        categorias: CollectionReference,
        perfil: Perfil,
        chaveMarcaTransacoes: String,
        chaveMarcaCategorias: String,
        listeners: MutableList<ListenerRegistration>,
        trabalhos: MutableList<Job>
    ) {
        // ---------- PULL (tempo real) ----------
        listeners += transacoes.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarComProtecao { aplicarTransacaoRemota(it, perfil) } } }
            }
        }
        listeners += categorias.addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarComProtecao { aplicarCategoriaRemota(it, perfil) } } }
            }
        }

        // ---------- PUSH (reativo, com debounce) ----------
        trabalhos += escopo.launch {
            transacaoDao.observarUltimaModificacao(perfil)
                .debounce(1_500)
                .collect {
                    runCatching { empurrarTransacoes(transacoes, perfil, chaveMarcaTransacoes) }
                }
        }
        trabalhos += escopo.launch {
            categoriaDao.observarUltimaModificacao(perfil)
                .debounce(1_500)
                .collect {
                    runCatching { empurrarCategorias(categorias, perfil, chaveMarcaCategorias) }
                }
        }
    }

    private fun parar(
        listeners: MutableList<ListenerRegistration>,
        trabalhos: MutableList<Job>
    ) {
        listeners.forEach { it.remove() }
        listeners.clear()
        trabalhos.forEach { it.cancel() }
        trabalhos.clear()
    }

    // ---------- PUSH ----------

    private suspend fun empurrarTransacoes(
        ref: CollectionReference,
        perfil: Perfil,
        chaveMarca: String,
        autorPadrao: String? = null,
        deletarTombstones: Boolean = false
    ) {
        val marca = prefs.getLong(chaveMarca, 0L)
        // Na Casa, empurra só os MEUS lançamentos (e os legados sem autor):
        // as regras negam escrita em doc de outro autor, e uma negação
        // derruba o batch inteiro (eco de docs puxados incluído).
        val meuUid = casaManager.usuario.value?.uid
        val todas = transacaoDao.listarModificadas(perfil, marca)
        if (todas.isEmpty()) return
        val modificadas = todas.filter { t ->
            perfil != Perfil.CASA ||
                t.criadoPorUid.isBlank() ||
                t.criadoPorUid == meuUid
        }

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { t ->
                // No espelho da casa (deletarTombstones), lançamento OCULTO some
                // do feed dos outros membros — tratado como uma remoção.
                if (deletarTombstones && (t.deletado || t.oculto)) {
                    batch.delete(ref.document(t.uuid))
                    return@forEach
                }
                batch.set(
                    ref.document(t.uuid),
                    mapOf(
                        "valor" to t.valor,
                        "tipo" to t.tipo.name,
                        "categoria" to t.categoria,
                        "descricao" to t.descricao,
                        "data" to t.data.toEpochDay(),
                        "atualizadoEm" to t.atualizadoEm,
                        "deletado" to t.deletado,
                        "criadoPor" to (autorPadrao ?: t.criadoPor),
                        "criadoPorUid" to t.criadoPorUid.ifBlank {
                            if (autorPadrao != null) meuUid.orEmpty() else ""
                        },
                        "transferenciaId" to t.transferenciaId,
                        "oculto" to t.oculto,
                        "cartaoUuid" to t.cartaoUuid,
                        "dataCompra" to t.dataCompra?.toEpochDay(),
                        "pago" to t.pago,
                        "dataPagamento" to t.dataPagamento?.toEpochDay(),
                        "recorrenciaUuid" to t.recorrenciaUuid
                    )
                )
            }
            // Fire-and-forget: offline, a escrita fica na fila durável do SDK
            batch.commit()
        }
        // A marca avança sobre TODAS as linhas listadas: docs de outros
        // autores nunca serão empurrados, não precisam ser re-listados.
        // Limitada ao relógio LOCAL: uma linha puxada de um aparelho com
        // relógio adiantado tem `atualizadoEm` no futuro; sem o teto, a marca
        // saltaria à frente e engoliria em silêncio tudo que eu editasse até
        // meu relógio alcançá-la.
        prefs.edit { putLong(chaveMarca, minOf(todas.maxOf { it.atualizadoEm }, agora())) }
    }

    private suspend fun empurrarCategorias(
        ref: CollectionReference,
        perfil: Perfil,
        chaveMarca: String
    ) {
        val marca = prefs.getLong(chaveMarca, 0L)
        val modificadas = categoriaDao.listarModificadas(perfil, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { c ->
                batch.set(
                    ref.document(c.uuid),
                    mapOf(
                        "nome" to c.nome,
                        "tipo" to c.tipo.name,
                        "cor" to c.cor,
                        "arquivada" to c.arquivada,
                        "orcamentoMensal" to c.orcamentoMensal,
                        "atualizadoEm" to c.atualizadoEm,
                        "deletado" to c.deletado
                    )
                )
            }
            batch.commit()
        }
        prefs.edit { putLong(chaveMarca, minOf(modificadas.maxOf { it.atualizadoEm }, agora())) }
    }

    private suspend fun empurrarCartoes(
        ref: CollectionReference,
        perfil: Perfil,
        chaveMarca: String
    ) {
        val marca = prefs.getLong(chaveMarca, 0L)
        val modificadas = cartaoDao.listarModificadas(perfil, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { c ->
                batch.set(
                    ref.document(c.uuid),
                    mapOf(
                        "nome" to c.nome,
                        "diaFechamento" to c.diaFechamento,
                        "diaVencimento" to c.diaVencimento,
                        "cor" to c.cor,
                        // Espelho de cartão pessoal: os outros membros também
                        // o tratam como read-only ("" = cartão nativo)
                        "origemUuid" to c.origemUuid,
                        "atualizadoEm" to c.atualizadoEm,
                        "deletado" to c.deletado
                    )
                )
            }
            batch.commit()
        }
        prefs.edit { putLong(chaveMarca, minOf(modificadas.maxOf { it.atualizadoEm }, agora())) }
    }

    private suspend fun empurrarMetas(
        ref: CollectionReference,
        perfil: Perfil,
        chaveMarca: String
    ) {
        val marca = prefs.getLong(chaveMarca, 0L)
        val modificadas = metaDao.listarModificadas(perfil, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { m ->
                batch.set(
                    ref.document(m.uuid),
                    mapOf(
                        "nome" to m.nome,
                        "valorAlvo" to m.valorAlvo,
                        "valorGuardado" to m.valorGuardado,
                        "prazo" to m.prazo?.toEpochDay(),
                        "cor" to m.cor,
                        "atualizadoEm" to m.atualizadoEm,
                        "deletado" to m.deletado
                    )
                )
            }
            batch.commit()
        }
        prefs.edit { putLong(chaveMarca, minOf(modificadas.maxOf { it.atualizadoEm }, agora())) }
    }

    private suspend fun empurrarContas(
        ref: CollectionReference,
        perfil: Perfil,
        chaveMarca: String
    ) {
        val marca = prefs.getLong(chaveMarca, 0L)
        val modificadas = contaAgendadaDao.listarModificadas(perfil, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { c ->
                batch.set(
                    ref.document(c.uuid),
                    mapOf(
                        "descricao" to c.descricao,
                        "valor" to c.valor,
                        "tipo" to c.tipo.name,
                        "categoria" to c.categoria,
                        "vencimento" to c.vencimento.toEpochDay(),
                        "pago" to c.pago,
                        "atualizadoEm" to c.atualizadoEm,
                        "deletado" to c.deletado
                    )
                )
            }
            batch.commit()
        }
        prefs.edit { putLong(chaveMarca, minOf(modificadas.maxOf { it.atualizadoEm }, agora())) }
    }

    // ---------- PULL ----------

    private suspend fun aplicarMetaRemota(doc: DocumentSnapshot, perfil: Perfil) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = metaDao.obterPorUuid(doc.id)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val meta = Meta(
            id = local?.id ?: 0,
            uuid = doc.id,
            nome = doc.getString("nome") ?: return,
            valorAlvo = doc.getLong("valorAlvo") ?: return,
            valorGuardado = doc.getLong("valorGuardado") ?: 0L,
            prazo = doc.getLong("prazo")?.let { LocalDate.ofEpochDay(it) },
            cor = doc.getString("cor") ?: "#10B981",
            perfil = perfil,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false
        )
        if (local == null) metaDao.inserir(meta) else metaDao.atualizar(meta)
    }

    private suspend fun aplicarContaRemota(doc: DocumentSnapshot, perfil: Perfil) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = contaAgendadaDao.obterPorUuid(doc.id)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val conta = ContaAgendada(
            id = local?.id ?: 0,
            uuid = doc.id,
            descricao = doc.getString("descricao") ?: return,
            valor = doc.getLong("valor") ?: return,
            tipo = parseTipo(doc.getString("tipo")) ?: return,
            categoria = doc.getString("categoria") ?: "Outros",
            vencimento = LocalDate.ofEpochDay(doc.getLong("vencimento") ?: return),
            pago = doc.getBoolean("pago") ?: false,
            perfil = perfil,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false
        )
        if (local == null) contaAgendadaDao.inserir(conta) else contaAgendadaDao.atualizar(conta)
    }

    private suspend fun aplicarTransacaoRemota(doc: DocumentSnapshot, perfil: Perfil) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = transacaoDao.obterPorUuid(doc.id)
        // Última edição vence; empate = mantém o local (evita eco)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val transacao = Transacao(
            id = local?.id ?: 0,
            uuid = doc.id,
            valor = doc.getLong("valor") ?: return,
            tipo = parseTipo(doc.getString("tipo")) ?: return,
            categoria = doc.getString("categoria") ?: "Outros",
            descricao = doc.getString("descricao") ?: "",
            data = LocalDate.ofEpochDay(doc.getLong("data") ?: return),
            perfil = perfil,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false,
            criadoPor = doc.getString("criadoPor") ?: "",
            criadoPorUid = doc.getString("criadoPorUid") ?: "",
            transferenciaId = doc.getString("transferenciaId") ?: "",
            oculto = doc.getBoolean("oculto") ?: false,
            cartaoUuid = doc.getString("cartaoUuid") ?: "",
            dataCompra = doc.getLong("dataCompra")?.let { LocalDate.ofEpochDay(it) },
            pago = doc.getBoolean("pago") ?: true,
            dataPagamento = doc.getLong("dataPagamento")?.let { LocalDate.ofEpochDay(it) },
            recorrenciaUuid = doc.getString("recorrenciaUuid") ?: "",
            // A nota fiscal é um arquivo local — nunca vem do remoto
            notaFiscal = local?.notaFiscal ?: ""
        )
        if (local == null) transacaoDao.inserir(transacao) else transacaoDao.atualizar(transacao)

        // Transferência deletada em outro aparelho: tombstona a outra perna
        // local também (ela pode viver num balde que só existe aqui)
        if (transacao.deletado && transacao.transferenciaId.isNotBlank()) {
            transacaoDao.listarPorTransferencia(transacao.transferenciaId)
                .filter { it.uuid != transacao.uuid && !it.deletado }
                .forEach {
                    transacaoDao.atualizar(
                        it.copy(deletado = true, atualizadoEm = System.currentTimeMillis())
                    )
                }
        }
    }

    private suspend fun aplicarCategoriaRemota(doc: DocumentSnapshot, perfil: Perfil) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = categoriaDao.obterPorUuid(doc.id)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val nome = doc.getString("nome") ?: return
        val tipo = parseTipo(doc.getString("tipo")) ?: return

        // Dedup semântico: cada aparelho semeia as categorias padrão com
        // uuids próprios — sem isso, o primeiro sync duplicaria todas.
        if (local == null &&
            categoriaDao.listarTodas(perfil).any {
                it.nome.equals(nome, ignoreCase = true) && it.tipo == tipo
            }
        ) {
            return
        }

        val categoria = Categoria(
            id = local?.id ?: 0,
            uuid = doc.id,
            nome = nome,
            tipo = tipo,
            cor = doc.getString("cor") ?: "#6B7280",
            perfil = perfil,
            arquivada = doc.getBoolean("arquivada") ?: false,
            orcamentoMensal = doc.getLong("orcamentoMensal") ?: 0L,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false
        )
        if (local == null) categoriaDao.inserir(categoria) else categoriaDao.atualizar(categoria)

        // Rename vindo de outro membro da Casa: quem renomeou só conseguiu
        // re-carimbar as PRÓPRIAS transações (regras negam as alheias) —
        // cada aparelho renomeia aqui as suas (e as antigas sem autor),
        // que então propagam normalmente pelo push.
        if (perfil == Perfil.CASA && local != null && !local.deletado && local.nome != nome) {
            val momento = agora()
            val meuUid = casaManager.usuario.value?.uid
            if (!meuUid.isNullOrBlank()) {
                transacaoDao.renomearCategoriaDoAutor(perfil, local.nome, nome, momento, meuUid)
            }
            transacaoDao.renomearCategoriaDoAutor(perfil, local.nome, nome, momento, uid = "")
            transacaoRecorrenteDao.renomearCategoria(perfil, local.nome, nome, momento)
        }
    }

    private suspend fun aplicarCartaoRemoto(doc: DocumentSnapshot, perfil: Perfil) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = cartaoDao.obterPorUuid(doc.id)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val cartao = Cartao(
            id = local?.id ?: 0,
            uuid = doc.id,
            nome = doc.getString("nome") ?: return,
            diaFechamento = (doc.getLong("diaFechamento") ?: return).toInt(),
            diaVencimento = (doc.getLong("diaVencimento") ?: return).toInt(),
            cor = doc.getString("cor") ?: "#8B5CF6",
            perfil = perfil,
            origemUuid = doc.getString("origemUuid") ?: "",
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false
        )
        if (local == null) cartaoDao.inserir(cartao) else cartaoDao.atualizar(cartao)
    }

    // ---------- Auxiliares ----------

    private fun transacoesCasaRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("transacoes")

    private fun categoriasCasaRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("categorias")

    private fun cartoesCasaRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("cartoes")

    private fun metasCasaRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("metas")

    private fun contasCasaRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("contas")

    private fun perfilPessoalRef(uid: String, perfil: Perfil) =
        db.collection("usuarios").document(uid)
            .collection("perfis").document(perfil.name.lowercase())

    private fun transacoesPessoalRef(uid: String, perfil: Perfil): CollectionReference =
        perfilPessoalRef(uid, perfil).collection("transacoes")

    private fun categoriasPessoalRef(uid: String, perfil: Perfil): CollectionReference =
        perfilPessoalRef(uid, perfil).collection("categorias")

    private fun cartoesPessoalRef(uid: String, perfil: Perfil): CollectionReference =
        perfilPessoalRef(uid, perfil).collection("cartoes")

    private fun metasPessoalRef(uid: String, perfil: Perfil): CollectionReference =
        perfilPessoalRef(uid, perfil).collection("metas")

    private fun contasPessoalRef(uid: String, perfil: Perfil): CollectionReference =
        perfilPessoalRef(uid, perfil).collection("contas")

    private fun parseTipo(nome: String?): TipoTransacao? =
        runCatching { TipoTransacao.valueOf(nome ?: "") }.getOrNull()

    private companion object {
        const val CHAVE_SYNC_PESSOAL = "sync_pessoal_ativado"
        const val CHAVE_COMPARTILHAR_CASA = "compartilhar_casa_ativado"
    }
}
