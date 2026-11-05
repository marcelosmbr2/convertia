package com.recast.app.data.engine.ffmpeg

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/**
 * O youtubedl-android extrai para o ffmpeg uma libwebp alinhada a 4 KB, que o Android não
 * carrega em aparelhos com páginas de 16 KB: o ffmpeg (que depende dela pelo libavcodec)
 * nem abre. O app compila a libwebp alinhada a 16 KB (`src/main/cpp`) e, nesses aparelhos,
 * troca as cópias extraídas por ela. Com páginas de 4 KB nada muda.
 */
object WebpLibraries {

    private val NAMES = listOf("libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so")

    /** Roda depois de cada inicialização do pacote do ffmpeg, que pode reextrair as originais. */
    fun install(context: Context) {
        if (Os.sysconf(OsConstants._SC_PAGESIZE) <= 4096) return
        val source = File(context.applicationInfo.nativeLibraryDir)
        val target = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        for (name in NAMES) {
            val from = File(source, name)
            val to = File(target, name)
            if (!from.isFile || !to.isFile) continue
            val bytes = from.readBytes()
            if (to.readBytes().contentEquals(bytes)) continue
            val temp = File(target, "$name.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(to)) {
                temp.delete()
                throw IOException("Não foi possível instalar $name para o ffmpeg")
            }
        }
    }
}
