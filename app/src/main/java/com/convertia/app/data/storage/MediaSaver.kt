package com.convertia.app.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.convertia.app.domain.ConflictPolicy
import com.convertia.app.domain.SavedFile
import java.io.File

/**
 * Grava arquivos prontos em `Download/Convertia`: pelo MediaStore no Android 10+ (sem
 * permissão) e direto na pasta pública, com o FileProvider, até o Android 9.
 */
class MediaSaver(private val context: Context) {

    /**
     * Grava [file] em `Download/Convertia`. Se já existir um arquivo com o mesmo nome:
     * RENAME acrescenta " (1)"; REPLACE apaga o antigo, se for do app (de outro dono,
     * renomeia); SKIP mantém o antigo e devolve-o sem gravar nada.
     */
    fun save(file: File, policy: ConflictPolicy = ConflictPolicy.RENAME): SavedFile {
        val mime = mimeTypeOf(file.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (policy) {
                ConflictPolicy.RENAME -> Unit
                ConflictPolicy.REPLACE -> existingInMediaStore(file.name)?.let { if (it.ownedByApp) context.contentResolver.delete(it.uri, null, null) }
                ConflictPolicy.SKIP -> existingInMediaStore(file.name)?.let { return SavedFile(it.uri.toString(), file.name, it.sizeBytes, mime) }
            }
            return saveToMediaStore(file, mime)
        }
        val existing = File(legacyFolder(), file.name)
        when (policy) {
            ConflictPolicy.RENAME -> Unit
            ConflictPolicy.REPLACE -> existing.delete()
            ConflictPolicy.SKIP -> if (existing.isFile) {
                val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), existing)
                return SavedFile(uri.toString(), existing.name, existing.length(), mime)
            }
        }
        return saveToPublicFolder(file, mime)
    }

    private class Existing(val uri: Uri, val sizeBytes: Long, val ownedByApp: Boolean)

    /** Arquivo com esse nome já em `Download/Convertia`, de qualquer app. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existingInMediaStore(name: String): Existing? {
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE, MediaStore.Downloads.OWNER_PACKAGE_NAME)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/")
        return context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            Existing(
                uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                sizeBytes = cursor.getLong(1),
                ownedByApp = cursor.getString(2) == context.packageName,
            )
        }
    }

    /** Apaga um arquivo salvo antes; `false` se não deu (por exemplo, criado por outra instalação). */
    fun delete(uri: String): Boolean = runCatching {
        val parsed = uri.toUri()
        if (parsed.authority == fileProviderAuthority(context)) {
            legacyFolder().resolve(parsed.lastPathSegment.orEmpty()).delete()
        } else {
            context.contentResolver.delete(parsed, null, null) > 0
        }
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(file: File, mime: String): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Download/$FOLDER")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Não foi possível gravar o arquivo")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        // O MediaStore renomeia se já existir um arquivo com o mesmo nome.
        val name = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: file.name
        return SavedFile(uri.toString(), name, file.length(), mime)
    }

    private fun saveToPublicFolder(file: File, mime: String): SavedFile {
        val folder = legacyFolder().apply { mkdirs() }
        val target = uniqueFile(folder, file.name)
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), target)
        return SavedFile(uri.toString(), target.name, target.length(), mime)
    }

    @Suppress("DEPRECATION")
    private fun legacyFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    private fun uniqueFile(folder: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = File(folder, name)
        var n = 1
        while (candidate.exists()) candidate = File(folder, "$base ($n)$ext").also { n++ }
        return candidate
    }

    companion object {
        const val FOLDER = "Convertia"

        fun fileProviderAuthority(context: Context) = "${context.packageName}.files"

        /** Tipo MIME pela extensão, cobrindo os formatos que o MimeTypeMap às vezes não conhece. */
        fun mimeTypeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "opus" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
        }
    }
}
