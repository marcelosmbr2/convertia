package com.convertia.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.ConflictPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Ajustes do app salvos no DataStore ("As alterações são salvas automaticamente"). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.onboarded] = updated.onboarded
            prefs[Keys.detectClipboardLinks] = updated.detectClipboardLinks
            prefs[Keys.notifications] = updated.notifications
            prefs[Keys.concurrentDownloads] = updated.concurrentDownloads
            prefs[Keys.wifiOnly] = updated.wifiOnly
            prefs[Keys.embedThumbnail] = updated.embedThumbnail
            prefs[Keys.embedMetadata] = updated.embedMetadata
            prefs[Keys.subtitles] = updated.subtitles
            prefs[Keys.splitChapters] = updated.splitChapters
            prefs[Keys.conflictPolicy] = updated.conflictPolicy.name
            prefs[Keys.parallelConversions] = updated.parallelConversions
            prefs[Keys.hardwareAcceleration] = updated.hardwareAcceleration
            prefs[Keys.keepOriginals] = updated.keepOriginals
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            onboarded = this[Keys.onboarded] ?: defaults.onboarded,
            detectClipboardLinks = this[Keys.detectClipboardLinks] ?: defaults.detectClipboardLinks,
            notifications = this[Keys.notifications] ?: defaults.notifications,
            concurrentDownloads = this[Keys.concurrentDownloads] ?: defaults.concurrentDownloads,
            wifiOnly = this[Keys.wifiOnly] ?: defaults.wifiOnly,
            embedThumbnail = this[Keys.embedThumbnail] ?: defaults.embedThumbnail,
            embedMetadata = this[Keys.embedMetadata] ?: defaults.embedMetadata,
            subtitles = this[Keys.subtitles] ?: defaults.subtitles,
            splitChapters = this[Keys.splitChapters] ?: defaults.splitChapters,
            conflictPolicy = this[Keys.conflictPolicy]
                ?.let { name -> ConflictPolicy.entries.firstOrNull { it.name == name } }
                ?: defaults.conflictPolicy,
            parallelConversions = this[Keys.parallelConversions] ?: defaults.parallelConversions,
            hardwareAcceleration = this[Keys.hardwareAcceleration] ?: defaults.hardwareAcceleration,
            keepOriginals = this[Keys.keepOriginals] ?: defaults.keepOriginals,
        )
    }

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val detectClipboardLinks = booleanPreferencesKey("detect_clipboard_links")
        val notifications = booleanPreferencesKey("notifications")
        val concurrentDownloads = intPreferencesKey("concurrent_downloads")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
        val embedMetadata = booleanPreferencesKey("embed_metadata")
        val subtitles = booleanPreferencesKey("subtitles")
        val splitChapters = booleanPreferencesKey("split_chapters")
        val conflictPolicy = stringPreferencesKey("conflict_policy")
        val parallelConversions = intPreferencesKey("parallel_conversions")
        val hardwareAcceleration = booleanPreferencesKey("hardware_acceleration")
        val keepOriginals = booleanPreferencesKey("keep_originals")
    }
}
