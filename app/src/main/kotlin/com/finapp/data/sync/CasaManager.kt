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
    private val repository: com.finapp.data.repository.FinanceRepository
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
        if (auth.currentUser == null) return
        runCatching {
            val doc = db.collection(COLECAO).document(casaId).get().await()
            _casa.value = doc.paraCasa()
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

    fun sairDaConta() {
        auth.signOut()
        _casa.value = null
    }

    /** Cria uma casa nova com código de convite único e entra nela. */
    suspend fun criarCasa(): Casa {
        val uid = uidLogado()
        var codigo = gerarCodigo()
        // Regenera em caso de colisão de código (raro)
        while (buscarPorCodigo(codigo) != null) {
            codigo = gerarCodigo()
        }

        val dados = mapOf(
            "codigoConvite" to codigo,
            "membros" to listOf(uid),
            "criadoPor" to uid,
            "criadoEm" to FieldValue.serverTimestamp()
        )
        val ref = db.collection(COLECAO).add(dados).await()
        val casa = Casa(id = ref.id, codigoConvite = codigo, membros = listOf(uid))
        // Só o criador semeia as categorias padrão; os demais recebem via sync
        repository.semearCategoriasCasa()
        salvarCasa(casa)
        return casa
    }

    /** Entra numa casa existente pelo código de convite. */
    suspend fun entrarNaCasa(codigo: String): Casa {
        val uid = uidLogado()
        val codigoLimpo = codigo.trim().uppercase()
        val encontrada = buscarPorCodigo(codigoLimpo)
            ?: throw IllegalArgumentException("Nenhuma casa com o código $codigoLimpo")

        db.collection(COLECAO).document(encontrada.id)
            .update("membros", FieldValue.arrayUnion(uid))
            .await()

        val casa = encontrada.copy(membros = encontrada.membros + uid)
        salvarCasa(casa)
        return casa
    }

    /** Sai da casa (os dados dos outros membros permanecem lá). */
    suspend fun sairDaCasa() {
        val casaAtual = _casa.value ?: return
        val uid = uidLogado()
        db.collection(COLECAO).document(casaAtual.id)
            .update("membros", FieldValue.arrayRemove(uid))
            .await()
        prefs.edit { remove(CHAVE_CASA) }
        _casa.value = null
    }

    // ---------- Internos ----------

    private fun uidLogado(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Faça login primeiro")

    private suspend fun buscarPorCodigo(codigo: String): Casa? =
        db.collection(COLECAO)
            .whereEqualTo("codigoConvite", codigo)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.paraCasa()

    private fun salvarCasa(casa: Casa) {
        prefs.edit { putString(CHAVE_CASA, casa.id) }
        _casa.value = casa
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
        const val COLECAO = "casas"
        const val CHAVE_CASA = "casa_id"
        const val TAMANHO_CODIGO = 6

        /** Sem 0/O/1/I para o código ser fácil de ditar. */
        const val ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
