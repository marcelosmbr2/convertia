package com.convertia.app.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.convertia.app.R
import com.convertia.app.domain.LibraryItem

/** Abre e compartilha arquivos da Biblioteca com os apps do aparelho. */
object LibraryIntents {

    /** Abre no player/visualizador padrão. `false` se não houver arquivo ou app que abra. */
    fun open(context: Context, item: LibraryItem): Boolean {
        val uri = item.uri?.toUri() ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /** Abre o menu Compartilhar do sistema com o arquivo. */
    fun share(context: Context, item: LibraryItem): Boolean {
        val uri = item.uri?.toUri() ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, context.getString(R.string.library_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, chooser)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
