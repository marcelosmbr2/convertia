package com.recast.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.recast.app.R
import com.recast.app.data.ToastEvent
import com.recast.app.data.ToastMessage
import com.recast.app.ui.components.ToastContent

/** Texto e ação de cada tipo de toast (as mensagens do mockup). */
@Composable
fun ToastMessage.toContent(): ToastContent {
    val (text, action) = when (val e = event) {
        is ToastEvent.DownloadFinished ->
            stringResource(R.string.toast_download_finished) to stringResource(R.string.action_convert)
        ToastEvent.ConversionsFinished ->
            stringResource(R.string.toast_conversions_finished) to stringResource(R.string.action_history)
        is ToastEvent.DownloadQueued ->
            stringResource(R.string.toast_download_queued, e.formatLabel) to stringResource(R.string.action_view_queue)
        is ToastEvent.SharedDownloadQueued ->
            stringResource(R.string.toast_shared_download_queued, e.formatLabel) to stringResource(R.string.action_open)
        is ToastEvent.LibraryItemDeleted ->
            stringResource(R.string.toast_file_deleted) to stringResource(R.string.action_undo)
        is ToastEvent.FilesAdded ->
            pluralStringResource(R.plurals.toast_files_added, e.count, e.count) to null
        is ToastEvent.FilesUnsupported ->
            pluralStringResource(R.plurals.toast_files_unsupported, e.count, e.count) to null
        is ToastEvent.TargetSetForAll ->
            (if (e.count == 0) stringResource(R.string.toast_no_compatible_files)
            else pluralStringResource(R.plurals.toast_target_set, e.count, e.count, e.ext)) to null
        is ToastEvent.PresetApplied ->
            (if (e.count == 0) stringResource(R.string.toast_preset_no_match, e.sources.joinToString("/") { it.uppercase() })
            else pluralStringResource(R.plurals.toast_preset_applied, e.count, e.name, e.count)) to null
        is ToastEvent.OptionsApplied ->
            pluralStringResource(R.plurals.toast_options_applied, e.count, e.count) to null
        ToastEvent.FileUnavailable -> stringResource(R.string.toast_file_unavailable) to null
        is ToastEvent.EngineUpToDate -> stringResource(R.string.toast_engine_up_to_date, e.engine, e.version).trim() to null
        is ToastEvent.EngineUpdated -> stringResource(R.string.toast_engine_updated, e.engine, e.version) to null
        ToastEvent.EngineUpdateFailed -> stringResource(R.string.toast_engine_update_failed) to null
        ToastEvent.NoLinkShared -> stringResource(R.string.toast_no_link_shared) to null
        ToastEvent.NoLinkInClipboard -> stringResource(R.string.toast_no_link_clipboard) to null
    }
    return ToastContent(id, text, action)
}
