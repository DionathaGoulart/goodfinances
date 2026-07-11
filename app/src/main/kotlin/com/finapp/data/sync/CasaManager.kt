package com.finapp.data.sync

import android.content.Context
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Usuário logado (dados mínimos para a UI). */
data class UsuarioCasa(
    val uid: String,
    val nome: String,
    val email: String
)

/** A "Casa": carteira compartilhada entre membros. */
data class Casa(
    val id: String,
    val codigoConvite: String,
    val membros: List<String>
)

/**
 * Login Google (Credential Manager) + gestão da Casa no Firestore.
 * A Casa é um documento em `casas/{id}` com código de convite de 6
 * caracteres; entrar na casa = estar na lista `membros`.
 * O sync dos dados em si é a Fase 3.
 */
@Singleton
class CasaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: com.finapp.data.repository.FinanceRepository,
    private val perfilManager: com.finapp.data.PerfilManager
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _usuario = MutableStateFlow(auth.currentUser?.paraUsuario())
    val usuario: StateFlow<UsuarioCasa?> = _usuario.asStateFlow()

    private val _casa = MutableStateFlow<Casa?>(null)
    val casa: StateFlow<Casa?> = _casa.asStateFlow()

    init {
        auth.addAuthStateListener { _usuario.value = it.currentUser?.paraUsuario() }
    }

    /** Recarrega a casa salva (chamar ao abrir a tela; tolera estar offline). */
    suspend fun carregarCasa() {
        val casaId = prefs.getString(CHAVE_CASA, null) ?: return
        val uidAtual = auth.currentUser?.uid ?: return
        // A casa salva pertence à CONTA que a salvou: se o usuário trocou de
        // conta Google, o vínculo (e o balde CASA local) é da conta antiga —
        // mantê-lo misturaria/empurraria o histórico dela para a casa nova.
        val donoUid = prefs.getString(CHAVE_CASA_UID, null)
        if (donoUid != null && donoUid != uidAtual) {
            limparVinculoLocalDaCasa()
            return
        }
        runCatching {
            val doc = db.collection(COLECAO).document(casaId).get().await()
            _casa.value = doc.paraCasa()
            // Backfill de instalações antigas (casa salva sem o uid do dono)
            if (donoUid == null && _casa.value != null) {
                prefs.edit { putString(CHAVE_CASA_UID, uidAtual) }
            }
            // Casas criadas antes da coleção de convites: registra o código
            // para novos membros conseguirem entrar (as regras não deixam
            // mais procurar casas pelo código diretamente)
            _casa.value?.let { garantirConvite(it) }
            // Reconcilia os espelhos dos cartões pessoais na Casa: cobre
            // cartões de antes da feature e os que chegaram pelo sync
            // pessoal direto no DAO (sem passar pelo repository)
            if (_casa.value != null) {
                runCatching { repository.espelharCartoesPessoaisNaCasa() }
            }
        }.onFailure {
            android.util.Log.w(TAG, "Falha ao carregar a casa $casaId", it)
        }
    }

    /** Cria o doc `convites/{codigo}` se ainda não existir (auto-reparo). */
    private suspend fun garantirConvite(casa: Casa) {
        runCatching {
            val convite = conviteRef(casa.codigoConvite).get().await()
            if (!convite.exists()) {
                conviteRef(casa.codigoConvite).set(mapOf("casaId" to casa.id)).await()
            }
        }
    }

    /**
     * Fluxo completo de login Google. [contextoActivity] precisa ser a
     * Activity (o Credential Manager mostra UI de escolha de conta).
     */
    suspend fun entrarComGoogle(contextoActivity: Context) {
        val webClientId = obterWebClientId()
        val opcaoGoogle = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val pedido = GetCredentialRequest.Builder()
            .addCredentialOption(opcaoGoogle)
            .build()

        val resultado = CredentialManager.create(contextoActivity)
            .getCredential(contextoActivity, pedido)

        val credencial = resultado.credential
        check(
            credencial is CustomCredential &&
                credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Credencial inesperada do Google" }

        val idToken = GoogleIdTokenCredential.createFrom(credencial.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        carregarCasa()
    }

    /**
     * Sai da conta Google. Limpa também o vínculo local com a casa: ele é da
     * CONTA que saiu — sem isso, logar com outra conta e entrar noutra casa
     * mistura o histórico antigo (e o empurra para a casa nova, cuja marca de
     * push começa em zero). Os dados continuam no Firestore e voltam via sync
     * se a mesma conta relogar.
     */
    suspend fun sairDaConta() {
        auth.signOut()
        limparVinculoLocalDaCasa()
    }

    /** Desfaz o vínculo local com a casa (prefs, flag do seletor e balde CASA). */
    private suspend fun limparVinculoLocalDaCasa() {
        prefs.edit {
            remove(CHAVE_CASA)
            remove(CHAVE_CASA_UID)
        }
        _casa.value = null
        perfilManager.definirTemCasa(false)
        repository.limparDadosLocaisDaCasa()
    }

    /** Cria uma casa nova com código de convite único e entra nela. */
    suspend fun criarCasa(): Casa {
        val uid = uidLogado()
        check(_casa.value == null) { "Você já está numa casa — saia dela primeiro" }
        var codigo = gerarCodigo()
        // Regenera em caso de colisão de código (raro)
        while (conviteRef(codigo).get().await().exists()) {
            codigo = gerarCodigo()
        }

        // A casa é criada ANTES do convite: a regra de criação do convite
        // exige que quem cria já seja membro da casa apontada (impede apontar
        // um código para a casa alheia). Num batch o `get()` da regra não
        // enxergaria a casa ainda não commitada, então são duas escritas.
        val ref = db.collection(COLECAO).document()
        ref.set(
            mapOf(
                "codigoConvite" to codigo,
                "membros" to listOf(uid),
                "criadoPor" to uid,
                "criadoEm" to FieldValue.serverTimestamp()
            )
        ).await()
        // O convite é o único caminho público até a casa (as regras não
        // deixam listar casas nem ler as dos outros).
        conviteRef(codigo).set(mapOf("casaId" to ref.id)).await()

        val casa = Casa(id = ref.id, codigoConvite = codigo, membros = listOf(uid))
        // Só o criador semeia as categorias padrão; os demais recebem via sync
        repository.semearCategoriasCasa()
        salvarCasa(casa)
        // Meus cartões pessoais aparecem na Casa (espelho; sobe pelo sync)
        runCatching { repository.espelharCartoesPessoaisNaCasa() }
        return casa
    }

    /** Entra numa casa existente pelo código de convite. */
    suspend fun entrarNaCasa(codigo: String): Casa {
        val uid = uidLogado()
        check(_casa.value == null) { "Você já está numa casa — saia dela primeiro" }
        val codigoLimpo = codigo.trim().uppercase()
        // O convite resolve o código -> casa (get direto; sem varrer casas)
        val casaId = conviteRef(codigoLimpo).get().await().getString("casaId")
            ?: throw IllegalArgumentException("Nenhuma casa com o código $codigoLimpo")

        // Entra primeiro (a regra permite o update que ADICIONA o próprio
        // uid); só membro consegue ler o doc da casa
        db.collection(COLECAO).document(casaId)
            .update("membros", FieldValue.arrayUnion(uid))
            .await()

        val casa = db.collection(COLECAO).document(casaId).get().await().paraCasa()
            ?: throw IllegalStateException("Casa não encontrada — peça um código novo")
        salvarCasa(casa)
        // Meus cartões pessoais aparecem na Casa (espelho; sobe pelo sync)
        runCatching { repository.espelharCartoesPessoaisNaCasa() }
        return casa
    }

    /** Sai da casa (os dados dos outros membros permanecem lá). */
    suspend fun sairDaCasa() {
        val casaAtual = _casa.value ?: return
        val uid = uidLogado()
        // Remove o espelho dos meus lançamentos pessoais ANTES de sair
        // (depois de sair das `membros`, as regras negam a escrita)
        runCatching { apagarEspelhoRemoto(casaAtual.id, uid) }
        // Tombstona na Casa os espelhos dos meus cartões pessoais: sem isso
        // eles ficariam órfãos permanentes nos aparelhos dos outros membros
        // (o delete físico local não propaga; só o tombstone via sync)
        runCatching { tombstonarEspelhosDeCartoes(casaAtual.id) }
        db.collection(COLECAO).document(casaAtual.id)
            .update("membros", FieldValue.arrayRemove(uid))
            .await()
        // Limpa prefs + balde local (delete físico, o sync já parou): sem
        // isso, entrar em OUTRA casa empurraria o histórico da antiga
        limparVinculoLocalDaCasa()
    }

    /** Tombstona em `casas/{id}/cartoes` os espelhos dos meus cartões pessoais. */
    private suspend fun tombstonarEspelhosDeCartoes(casaId: String) {
        val agora = System.currentTimeMillis()
        val cartoesRef = db.collection(COLECAO).document(casaId).collection("cartoes")
        com.finapp.data.db.entities.Perfil.BALDES_PESSOAIS
            .flatMap { repository.listarCartoes(it) }
            .forEach { cartao ->
                cartoesRef
                    .document(
                        com.finapp.data.repository.FinanceRepository
                            .uuidEspelhoCartao(cartao.uuid)
                    )
                    .set(
                        mapOf("deletado" to true, "atualizadoEm" to agora),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }
    }

    /** Apaga em lotes todos os docs do espelho pessoal deste membro. */
    private suspend fun apagarEspelhoRemoto(casaId: String, uid: String) {
        val ref = db.collection(COLECAO).document(casaId)
            .collection("membros").document(uid)
            .collection("transacoes")
        while (true) {
            val docs = ref.limit(400).get().await().documents
            if (docs.isEmpty()) break
            val batch = db.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ---------- Internos ----------

    private fun uidLogado(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Faça login primeiro")

    private fun conviteRef(codigo: String) =
        db.collection(COLECAO_CONVITES).document(codigo)

    private fun salvarCasa(casa: Casa) {
        prefs.edit {
            putString(CHAVE_CASA, casa.id)
            // Vínculo é por conta: detecta troca de conta no carregarCasa
            putString(CHAVE_CASA_UID, auth.currentUser?.uid)
        }
        _casa.value = casa
        perfilManager.definirTemCasa(true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.paraCasa(): Casa? {
        if (!exists()) return null
        return Casa(
            id = id,
            codigoConvite = getString("codigoConvite") ?: return null,
            membros = (get("membros") as? List<String>).orEmpty()
        )
    }

    private fun com.google.firebase.auth.FirebaseUser.paraUsuario() =
        UsuarioCasa(uid = uid, nome = displayName ?: "Sem nome", email = email ?: "")

    /**
     * O id do cliente web vem do google-services.json. Se o recurso não
     * existir, o login Google ainda não foi habilitado no console.
     */
    private fun obterWebClientId(): String {
        val idRecurso = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        check(idRecurso != 0) {
            "Login Google não configurado: habilite o provedor Google no Firebase " +
                "Authentication e substitua o google-services.json pelo novo"
        }
        return context.getString(idRecurso)
    }

    private fun gerarCodigo(): String =
        (1..TAMANHO_CODIGO)
            .map { ALFABETO_CODIGO.random() }
            .joinToString("")

    private companion object {
        const val TAG = "CasaManager"
        const val COLECAO = "casas"
        const val COLECAO_CONVITES = "convites"
        const val CHAVE_CASA = "casa_id"
        const val CHAVE_CASA_UID = "casa_uid"
        const val TAMANHO_CODIGO = 6

        /** Sem 0/O/1/I para o código ser fácil de ditar. */
        const val ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
