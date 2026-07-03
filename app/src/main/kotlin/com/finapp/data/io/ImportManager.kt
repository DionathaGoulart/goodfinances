package com.finapp.data.io

import android.content.Context
import android.net.Uri
import com.finapp.data.db.entities.Perfil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lê arquivos de importação do Storage Access Framework e delega ao parser. */
@Singleton
class ImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ParserImportacao
) {

    fun ler(uri: Uri, perfil: Perfil): DadosImportados {
        val texto = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("Não foi possível abrir o arquivo")
        return parser.lerTexto(texto, perfil)
    }

    fun lerTexto(texto: String, perfil: Perfil): DadosImportados =
        parser.lerTexto(texto, perfil)
}
