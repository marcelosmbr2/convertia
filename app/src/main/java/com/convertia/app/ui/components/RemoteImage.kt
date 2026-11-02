package com.convertia.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Cache pequeno de miniaturas já baixadas (sobrevive à troca de tela). */
private object ThumbnailCache : LruCache<String, ImageBitmap>(24)

private const val TARGET_WIDTH = 480
private const val MAX_BYTES = 4 * 1024 * 1024

/**
 * Miniatura remota (capa do vídeo). Enquanto carrega, ou se falhar, não desenha nada —
 * quem chama mostra a miniatura listrada por baixo.
 */
@Composable
fun RemoteThumbnail(url: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState(initialValue = url?.let { ThumbnailCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { download(url) }?.also { ThumbnailCache.put(url, it) }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

private fun download(url: String): ImageBitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        if (connection.contentLengthLong > MAX_BYTES) return@runCatching null
        val bytes = connection.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    } finally {
        connection.disconnect()
    }
}.getOrNull()
