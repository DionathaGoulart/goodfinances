package com.finapp.data.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda as notas fiscais/comprovantes anexados às transações (qualquer
 * contexto). Os arquivos vivem em `filesDir/notas/` (armazenamento interno —
 * sem permissões) e a transação referencia só o nome; abrir/tirar foto usa o
 * FileProvider declarado no manifest. Imagens são convertidas em PDF na
 * entrada, para o export fiscal sair 100% em PDF.
 */
@Singleton
class NotaFiscalManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pasta: File
        get() = File(context.filesDir, PASTA).apply { mkdirs() }

    /**
     * Copia o conteúdo da [uri] (galeria/arquivo) e retorna o nome gerado.
     * Imagens viram PDF automaticamente.
     */
    suspend fun anexar(uri: Uri): String = withContext(Dispatchers.IO) {
        val extensao = when (context.contentResolver.getType(uri)) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            else -> "jpg"
        }
        val nome = "${UUID.randomUUID()}.$extensao"
        val entrada = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não foi possível ler o arquivo")
        entrada.use { origem ->
            File(pasta, nome).outputStream().use { destino -> origem.copyTo(destino) }
        }
        if (extensao == "pdf") nome else converterImagemParaPdf(nome)
    }

    /**
     * Converte uma imagem já salva na pasta em PDF de página única (com a
     * rotação do EXIF aplicada) e apaga a original. Se a imagem não puder
     * ser decodificada, mantém o arquivo como está.
     */
    suspend fun converterImagemParaPdf(nome: String): String = withContext(Dispatchers.IO) {
        val origem = File(pasta, nome)
        if (!origem.exists() || nome.endsWith(".pdf", ignoreCase = true)) {
            return@withContext nome
        }

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origem.path, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return@withContext nome

        // Downsample para no máx. ~2048px no maior lado (evita OOM em fotos grandes)
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = calcularSample(maxOf(limites.outWidth, limites.outHeight))
        }
        val decodificado = BitmapFactory.decodeFile(origem.path, opcoes)
            ?: return@withContext nome
        val bitmap = aplicarRotacaoExif(decodificado, origem)

        val nomePdf = "${nome.substringBeforeLast('.')}.pdf"
        val documento = PdfDocument()
        try {
            val pagina = documento.startPage(
                PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            )
            pagina.canvas.drawBitmap(bitmap, 0f, 0f, null)
            documento.finishPage(pagina)
            File(pasta, nomePdf).outputStream().use { documento.writeTo(it) }
        } finally {
            documento.close()
            bitmap.recycle()
        }
        origem.delete()
        nomePdf
    }

    private fun calcularSample(maiorLado: Int): Int {
        var sample = 1
        while (maiorLado / (sample * 2) >= LADO_MAX_PDF) sample *= 2
        return sample
    }

    /** Fotos da câmera vêm com a rotação no EXIF — o PDF precisa dela aplicada. */
    private fun aplicarRotacaoExif(bitmap: Bitmap, arquivo: File): Bitmap {
        val graus = when (
            runCatching {
                ExifInterface(arquivo.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matriz = Matrix().apply { postRotate(graus) }
        val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (girado != bitmap) bitmap.recycle()
        return girado
    }

    /** Arquivo destino para a foto da câmera: (nome, uri para o TakePicture). */
    fun criarDestinoFoto(): Pair<String, Uri> {
        val nome = "${UUID.randomUUID()}.jpg"
        return nome to uriPara(nome)
    }

    /** Abre a nota no visualizador do sistema (imagem ou PDF). */
    fun abrir(nome: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriPara(nome), tipoMime(nome))
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        context.startActivity(intent)
    }

    /** Arquivo físico da nota (para o export em ZIP). */
    fun arquivo(nome: String): File = File(pasta, nome)

    /** Remove o arquivo (nome vazio é ignorado). */
    fun apagar(nome: String) {
        if (nome.isNotBlank()) File(pasta, nome).delete()
    }

    /**
     * Apaga arquivos que nenhuma transação referencia mais (ex: nota de uma
     * transação apagada sem desfazer). Chamado na abertura do app.
     * Arquivos recentes têm carência de 7 dias: em celular novo, as notas
     * restauradas do Drive não podem sumir antes do backup de transações.
     */
    suspend fun limparOrfas(referenciadas: Set<String>) = withContext(Dispatchers.IO) {
        val limite = System.currentTimeMillis() - CARENCIA_ORFAS_MILLIS
        pasta.listFiles()
            ?.filter { it.name !in referenciadas && it.lastModified() < limite }
            ?.forEach { it.delete() }
    }

    private fun uriPara(nome: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(pasta, nome)
    )

    private fun tipoMime(nome: String): String =
        if (nome.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"

    private companion object {
        const val PASTA = "notas"

        /** Maior lado da imagem ao converter para PDF, em pixels. */
        const val LADO_MAX_PDF = 2048

        /** Órfãs só são apagadas depois desta idade (7 dias). */
        const val CARENCIA_ORFAS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
