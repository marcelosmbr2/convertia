package com.recast.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recast.app.R
import com.recast.app.data.SampleData
import com.recast.app.domain.Formatters
import com.recast.app.domain.HistoryEntry
import com.recast.app.domain.LibraryItem
import com.recast.app.domain.MediaKind
import com.recast.app.ui.LibraryView
import com.recast.app.ui.TimeLabels
import com.recast.app.ui.components.FileTypeTile
import com.recast.app.ui.components.ListCard
import com.recast.app.ui.components.PillTextField
import com.recast.app.ui.components.RowDivider
import com.recast.app.ui.components.ScreenHeader
import com.recast.app.ui.components.SegmentedControl
import com.recast.app.ui.components.SegmentedSize
import com.recast.app.ui.components.StripedThumbnail
import com.recast.app.ui.components.TextPillButton
import com.recast.app.ui.text
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.CvIconSpec
import com.recast.app.ui.theme.MonoTextStyle
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.RecastIcons
import com.recast.app.ui.theme.RecastTheme
import com.recast.app.ui.theme.TextStyles
import java.time.Clock

@Composable
fun LibraryScreen(
    view: LibraryView,
    onViewChange: (LibraryView) -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    clock: Clock = Clock.systemDefaultZone(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LibraryContent(
        ui = ui,
        view = view,
        clock = clock,
        onViewChange = onViewChange,
        onQueryChange = viewModel::onQueryChange,
        onOpenItem = onOpenItem,
        onOpenHistory = { entry -> if (!LibraryIntents.open(context, entry.uri, entry.mimeType)) viewModel.fileUnavailable() },
        onClearHistory = viewModel::clearHistory,
    )
}

@Composable
fun LibraryContent(
    ui: LibraryUiState,
    view: LibraryView,
    clock: Clock,
    onViewChange: (LibraryView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.library_title),
            subtitle = pluralStringResource(R.plurals.library_count, ui.items.size, ui.items.size, Formatters.size(ui.totalBytes)),
        )
        SegmentedControl(
            options = LibraryView.entries,
            selected = view,
            onSelect = onViewChange,
            label = { stringResource(if (it == LibraryView.FILES) R.string.library_tab_files else R.string.library_tab_history) },
            size = SegmentedSize.Large,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when (view) {
            LibraryView.FILES -> FilesView(ui, clock, onQueryChange, onOpenItem)
            LibraryView.HISTORY -> HistoryView(ui.history, clock, onClearHistory, onOpenHistory)
        }
    }
}

@Composable
private fun FilesView(ui: LibraryUiState, clock: Clock, onQueryChange: (String) -> Unit, onOpenItem: (String) -> Unit) {
    PillTextField(
        value = ui.query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.library_search),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Column {
        ui.visibleItems.forEach { item -> LibraryRow(item, clock, onClick = { onOpenItem(item.id) }) }
        if (ui.visibleItems.isEmpty()) EmptyText(stringResource(R.string.library_empty))
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, clock: Clock, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StripedThumbnail(Modifier.size(width = 64.dp, height = 36.dp), shape = RoundedCornerShape(6.dp), stripe = 5.dp) {
            if (item.kind == MediaKind.AUDIO) {
                CvIcon(
                    RecastIcons.Music,
                    contentDescription = null,
                    size = 14.dp,
                    tint = Palette.TextMeta2,
                    strokeWidth = 2f,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = BaseTextStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.meta(), style = TextStyles.RowMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.duration, style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextSecondary))
            Text(
                TimeLabels.of(item.addedAt, clock, relative = false).text(dayOnly = true),
                style = BaseTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMeta2),
            )
        }
    }
}

@Composable
private fun HistoryView(history: List<HistoryEntry>, clock: Clock, onClear: () -> Unit, onOpen: (HistoryEntry) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.library_history_local),
            style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
            modifier = Modifier.weight(1f),
        )
        if (history.isNotEmpty()) {
            TextPillButton(
                text = stringResource(R.string.library_clear),
                onClick = onClear,
                color = Palette.TextSecondary,
                fontWeight = FontWeight.Medium,
                horizontalPadding = 10.dp,
            )
        }
    }
    Column {
        history.forEach { entry -> HistoryRow(entry, clock, onClick = { onOpen(entry) }) }
        if (history.isEmpty()) EmptyText(stringResource(R.string.library_history_empty))
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, clock: Clock, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (entry.uri != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileTypeTile(entry.fromExt, width = 30.dp, height = 38.dp, radius = 6.dp, fontSize = 7.5.sp, bottomPadding = 4.dp, borderAlpha = 0.3f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.name, style = BaseTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    append("${entry.fromExt} → ${entry.toExt}")
                    withStyle(SpanStyle(color = Palette.TextMuted)) { append("  ·  ") }
                    append("${Formatters.size(entry.inputBytes)} → ${Formatters.size(entry.outputBytes)}")
                    if (entry.fileCount > 1) {
                        withStyle(SpanStyle(color = Palette.TextMuted)) { append("  ·  ") }
                        append(pluralStringResource(R.plurals.library_history_files, entry.fileCount, entry.fileCount))
                    }
                },
                style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMeta),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            TimeLabels.of(entry.at, clock, relative = false).text(),
            style = BaseTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMeta2),
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextTertiary),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
    )
}

/** "MP4 · 1920×1080 · 538 MB". */
private fun LibraryItem.meta(): String = "$format · $detail · ${Formatters.size(sizeBytes)}"

// Sheet de ações ----------------------------------------------------------------------------

data class LibraryItemActions(
    val onConvert: () -> Unit,
    val onPlay: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun ColumnScope.LibraryItemSheet(item: LibraryItem, actions: LibraryItemActions) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StripedThumbnail(Modifier.size(width = 72.dp, height = 40.dp), shape = RoundedCornerShape(7.dp), stripe = 5.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = BaseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp))
                Text("${item.meta()} · ${item.duration}", style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary))
            }
        }
        ListCard {
            ActionRow(RecastIcons.Convert, stringResource(R.string.action_convert), actions.onConvert, strokeWidth = 1.8f, weight = FontWeight.Medium)
            RowDivider()
            ActionRow(RecastIcons.Play, stringResource(R.string.library_open_player), actions.onPlay)
            RowDivider()
            ActionRow(RecastIcons.Share, stringResource(R.string.library_share), actions.onShare)
            RowDivider()
            ActionRow(RecastIcons.Trash, stringResource(R.string.library_delete), actions.onDelete, color = Palette.DangerText)
        }
    }
}

@Composable
private fun ActionRow(
    icon: CvIconSpec,
    label: String,
    onClick: () -> Unit,
    color: Color = Palette.TextPrimary,
    strokeWidth: Float = 1.7f,
    weight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CvIcon(icon, contentDescription = null, size = 19.dp, tint = if (color == Palette.TextPrimary) Palette.TextEmphasis else color, strokeWidth = strokeWidth)
        Text(label, style = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = weight, color = color))
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun LibraryPreview() {
    val clock = Clock.systemDefaultZone()
    RecastTheme {
        LibraryContent(
            LibraryUiState(SampleData.libraryItems(clock), SampleData.history(clock)),
            LibraryView.FILES, clock, {}, {}, {}, {},
        )
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun HistoryPreview() {
    val clock = Clock.systemDefaultZone()
    RecastTheme {
        LibraryContent(
            LibraryUiState(SampleData.libraryItems(clock), SampleData.history(clock)),
            LibraryView.HISTORY, clock, {}, {}, {}, {},
        )
    }
}
