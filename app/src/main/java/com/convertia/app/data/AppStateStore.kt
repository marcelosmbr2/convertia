package com.convertia.app.data

import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.DownloadStatus
import com.convertia.app.domain.HistoryEntry
import com.convertia.app.domain.LibraryItem
import com.convertia.app.domain.RecentLink
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** O que sobrevive ao fechar o app. */
@Serializable
data class AppSnapshot(
    val library: List<LibraryItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val recents: List<RecentLink> = emptyList(),
    val jobs: List<DownloadJob> = emptyList(),
) {
    /** Downloads que estavam rodando quando o processo morreu voltam pausados. */
    fun withInterruptedJobsPaused(): AppSnapshot = copy(
        jobs = jobs.map {
            if (it.isActive) it.copy(status = DownloadStatus.PAUSED, speedMbps = 0.0) else it
        },
    )
}

/** Guarda o [AppSnapshot] num arquivo JSON, com escrita atômica (arquivo temporário + rename). */
class AppStateStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun load(): AppSnapshot? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString(AppSnapshot.serializer(), file.readText())
    }.getOrNull()

    @Synchronized
    fun save(snapshot: AppSnapshot) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(AppSnapshot.serializer(), snapshot))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
