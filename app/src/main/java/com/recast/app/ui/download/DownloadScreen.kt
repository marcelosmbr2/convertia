package com.recast.app.ui.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recast.app.R
import com.recast.app.data.SampleData
import com.recast.app.domain.AppSettings
import com.recast.app.domain.DownloadFormat
import com.recast.app.domain.FormatBadge
import com.recast.app.domain.Formatters
import com.recast.app.domain.MediaInfo
import com.recast.app.domain.MediaKind
import com.recast.app.domain.RecentLink
import com.recast.app.ui.TimeLabels
import com.recast.app.ui.components.BottomActionBar
import com.recast.app.ui.components.ChoiceChip
import com.recast.app.ui.components.DurationBadge
import com.recast.app.ui.components.IconCircleButton
import com.recast.app.ui.components.ListCard
import com.recast.app.ui.components.PillButton
import com.recast.app.ui.components.PillTextField
import com.recast.app.ui.components.PrimaryButton
import com.recast.app.ui.components.RadioRow
import com.recast.app.ui.components.RemoteThumbnail
import com.recast.app.ui.components.RowDivider
import com.recast.app.ui.components.ScreenHeader
import com.recast.app.ui.components.SectionLabel
import com.recast.app.ui.components.SegmentedControl
import com.recast.app.ui.components.SegmentedSize
import com.recast.app.ui.components.StripedThumbnail
import com.recast.app.ui.components.TextPillButton
import com.recast.app.ui.components.ToggleRow
import com.recast.app.ui.text
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.MonoTextStyle
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.RecastIcons
import com.recast.app.ui.theme.RecastTheme
import com.recast.app.ui.theme.TextStyles
import java.time.Clock

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory),
    clock: Clock = Clock.systemDefaultZone(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ClipboardLinkWatcher(
        enabled = ui.settings.detectClipboardLinks,
        shouldRead = viewModel::shouldReadClipboard,
        onRead = viewModel::onClipboardChecked,
    )

    DownloadContent(
        ui = ui,
        clock = clock,
        actions = DownloadActions(
            onUrlChange = viewModel::onUrlChange,
            onInspect = viewModel::inspectCurrent,
            onInspectUrl = viewModel::inspect,
            onPaste = { viewModel.onPaste(readClipboardText(context)) },
            onClear = viewModel::clearUrl,
            onSelectKind = viewModel::selectKind,
            onSelectFormat = viewModel::selectFormat,
            onSelectContainer = viewModel::selectContainer,
            onToggleFormats = viewModel::toggleShowAllFormats,
            onToggleMore = viewModel::toggleMoreOptions,
            onEmbedThumbnail = viewModel::setEmbedThumbnail,
            onEmbedMetadata = viewModel::setEmbedMetadata,
            onSubtitles = viewModel::setSubtitles,
            onSplitChapters = viewModel::setSplitChapters,
            onDownload = viewModel::download,
        ),
    )
}

/** Callbacks da tela, agrupados para os previews. */
data class DownloadActions(
    val onUrlChange: (String) -> Unit = {},
    val onInspect: () -> Unit = {},
    val onInspectUrl: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onSelectKind: (MediaKind) -> Unit = {},
    val onSelectFormat: (String) -> Unit = {},
    val onSelectContainer: (String) -> Unit = {},
    val onToggleFormats: () -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onEmbedThumbnail: (Boolean) -> Unit = {},
    val onEmbedMetadata: (Boolean) -> Unit = {},
    val onSubtitles: (Boolean) -> Unit = {},
    val onSplitChapters: (Boolean) -> Unit = {},
    val onDownload: () -> Unit = {},
)

@Composable
fun DownloadContent(ui: DownloadUiState, clock: Clock, actions: DownloadActions) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.download_title), stringResource(R.string.download_subtitle))
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UrlInput(ui, actions)
                when (ui.phase) {
                    InspectPhase.EMPTY -> EmptyState(ui, clock, actions)
                    InspectPhase.LOADING -> LoadingState()
                    InspectPhase.READY -> ui.media?.let { ReadyState(ui, it, actions) }
                }
            }
        }
        if (ui.phase == InspectPhase.READY && ui.media != null) {
            SummaryBar(ui, actions.onDownload)
        }
    }
}

@Composable
private fun UrlInput(ui: DownloadUiState, actions: DownloadActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillTextField(
            value = ui.url,
            onValueChange = actions.onUrlChange,
            placeholder = stringResource(R.string.download_url_placeholder),
            leadingIcon = RecastIcons.Link,
            leadingIconSize = 18.dp,
            leadingIconStroke = 1.7f,
            height = 52.dp,
            radius = 14.dp,
            border = Palette.BorderStrong,
            contentPadding = PaddingValues(start = 14.dp, end = 4.dp),
            textStyle = BaseTextStyle.copy(fontSize = 14.5.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { actions.onInspect() }),
            trailing = {
                if (ui.url.isEmpty()) {
                    PillButton(
                        onClick = actions.onPaste,
                        height = 40.dp,
                        background = Palette.Raised3,
                        pressedBackground = Palette.BorderStrong,
                    ) {
                        CvIcon(RecastIcons.Clipboard, contentDescription = null, size = 15.dp)
                        Text(stringResource(R.string.download_paste), style = TextStyles.Button.copy(fontSize = 13.5.sp))
                    }
                } else {
                    IconCircleButton(
                        icon = RecastIcons.Close,
                        contentDescription = stringResource(R.string.download_clear),
                        onClick = actions.onClear,
                        size = 44.dp,
                        tint = Palette.TextMeta,
                    )
                }
            },
        )
        if (ui.showInspectButton) {
            PrimaryButton(
                text = stringResource(R.string.download_see_formats),
                onClick = actions.onInspect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.state.failed) {
            Text(
                stringResource(R.string.download_inspect_failed),
                style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.DangerText),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// Estado vazio ------------------------------------------------------------------------------

@Composable
private fun EmptyState(ui: DownloadUiState, clock: Clock, actions: DownloadActions) {
    ui.clipboardSuggestion?.let { ClipboardCard(it, onInspect = { actions.onInspectUrl(it) }) }
    if (ui.recents.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.download_recent))
            ListCard {
                ui.recents.forEachIndexed { index, recent ->
                    if (index > 0) RowDivider()
                    RecentRow(recent, clock, onClick = { actions.onInspectUrl(recent.url) })
                }
            }
        }
    }
    ShareTipCard()
}

@Composable
private fun ClipboardCard(link: String, onInspect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Raised3)
            .border(1.dp, Palette.BorderStrong, shape)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CvIcon(RecastIcons.Clipboard, contentDescription = null, size = 18.dp, tint = Palette.TextEmphasis)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.download_clipboard_title), style = BaseTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
            Text(
                link.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextPillButton(
            text = stringResource(R.string.download_inspect),
            onClick = onInspect,
            fontSize = 13.5.sp,
            height = 40.dp,
            horizontalPadding = 14.dp,
        )
    }
}

@Composable
private fun RecentRow(recent: RecentLink, clock: Clock, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CvIcon(RecastIcons.Clock, contentDescription = null, size = 18.dp, tint = Palette.TextMeta2, strokeWidth = 1.6f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(recent.title, style = BaseTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = TimeLabels.of(recent.at, clock, relative = true).text()
            Text("${recent.site} · $time", style = TextStyles.RowMeta, maxLines = 1)
        }
    }
}

@Composable
private fun ShareTipCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Palette.BorderDashed,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CvIcon(RecastIcons.Share, contentDescription = null, size = 20.dp, tint = Palette.TextSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.download_tip_title), style = BaseTextStyle.copy(fontWeight = FontWeight.Medium))
            Text(
                stringResource(R.string.download_tip_body),
                style = BaseTextStyle.copy(fontSize = 12.5.sp, lineHeight = 18.sp, color = Palette.TextMeta),
            )
        }
    }
}

// Carregando --------------------------------------------------------------------------------

@Composable
private fun LoadingState() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Palette.Sheet))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Raised))
        Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Sheet))
        repeat(2) {
            Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Card))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.TextPrimary))
            Text(stringResource(R.string.download_loading), style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextTertiary))
        }
    }
}

// Pronto ------------------------------------------------------------------------------------

@Composable
private fun ReadyState(ui: DownloadUiState, media: MediaInfo, actions: DownloadActions) {
    MediaCard(media)
    SegmentedControl(
        options = MediaKind.entries,
        selected = ui.state.kind,
        onSelect = actions.onSelectKind,
        label = { stringResource(if (it == MediaKind.VIDEO) R.string.download_tab_video else R.string.download_tab_audio) },
        size = SegmentedSize.Large,
    )
    FormatsCard(ui, actions)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.download_save_as))
        ContainerChips(ui, actions.onSelectContainer)
    }
    MoreOptionsCard(ui, actions)
    Text(
        stringResource(R.string.download_disclaimer),
        style = BaseTextStyle.copy(fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextMeta2),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaCard(media: MediaInfo) {
    ListCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StripedThumbnail(Modifier.size(width = 120.dp, height = 68.dp)) {
                    RemoteThumbnail(media.thumbnailUrl)
                    if (media.duration.isNotEmpty()) DurationBadge(media.duration)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        media.title,
                        style = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.5.sp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(media.channel, media.site).filter { it.isNotBlank() }.joinToString(" · "),
                        style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextMeta),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                media.license?.let { MediaChip(it, withCheck = true) }
                if (media.chapterCount > 0) {
                    MediaChip(pluralStringResource(R.plurals.download_chapters, media.chapterCount, media.chapterCount))
                }
                if (media.subtitleLanguages.isNotEmpty()) {
                    MediaChip(stringResource(R.string.download_subtitles_chip, media.subtitleLanguages.joinToString(", ")))
                }
            }
        }
    }
}

@Composable
private fun MediaChip(text: String, withCheck: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, if (withCheck) Palette.Border else Palette.BorderStrong, shape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (withCheck) CvIcon(RecastIcons.Check, contentDescription = null, size = 12.dp, tint = Palette.TextSecondary, strokeWidth = 2.2f)
        Text(text, style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextSecondary), maxLines = 1)
    }
}

@Composable
private fun FormatsCard(ui: DownloadUiState, actions: DownloadActions) {
    val selected = ui.selectedFormat
    ListCard {
        ui.visibleFormats.forEachIndexed { index, format ->
            RadioRow(
                title = format.quality,
                subtitle = format.subtitle(),
                badge = format.badge?.let { stringResource(it.label()) },
                selected = format == selected,
                onClick = { actions.onSelectFormat(format.id) },
                showDivider = index > 0,
                trailing = {
                    Text(sizeLabel(format.sizeMb), style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextEmphasis))
                },
            )
        }
        if (ui.showFormatsToggle) {
            RowDivider()
            val rotation by animateFloatAsState(if (ui.formatsExpanded) 180f else 0f, tween(200), label = "chevron")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(role = Role.Button, onClick = actions.onToggleFormats),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (ui.formatsExpanded) stringResource(R.string.download_show_less)
                    else pluralStringResource(R.plurals.download_show_all, ui.formats.size, ui.formats.size),
                    style = TextStyles.Button.copy(fontSize = 13.5.sp),
                )
                CvIcon(RecastIcons.ChevronDown, contentDescription = null, size = 15.dp, strokeWidth = 2f, modifier = Modifier.rotate(rotation))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContainerChips(ui: DownloadUiState, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.containers.forEach { container ->
            ChoiceChip(
                text = container.uppercase(),
                selected = container == ui.state.container,
                onClick = { onSelect(container) },
            )
        }
    }
}

@Composable
private fun MoreOptionsCard(ui: DownloadUiState, actions: DownloadActions) {
    val rotation by animateFloatAsState(if (ui.state.moreOpen) 180f else 0f, tween(200), label = "more")
    ListCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = actions.onToggleMore)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.download_more_options), style = TextStyles.RowTitle, modifier = Modifier.weight(1f))
            CvIcon(RecastIcons.ChevronDown, contentDescription = null, size = 18.dp, tint = Palette.TextMeta, strokeWidth = 2f, modifier = Modifier.rotate(rotation))
        }
        if (ui.state.moreOpen) {
            val s = ui.settings
            OptionToggle(stringResource(R.string.download_embed_thumbnail), s.embedThumbnail, actions.onEmbedThumbnail)
            OptionToggle(stringResource(R.string.download_embed_metadata), s.embedMetadata, actions.onEmbedMetadata)
            OptionToggle(stringResource(R.string.download_subtitles_en), s.subtitles, actions.onSubtitles)
            OptionToggle(stringResource(R.string.download_split_chapters), s.splitChapters, actions.onSplitChapters)
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.download_file_name), style = BaseTextStyle)
                    Text(ui.fileNamePreview, style = TextStyles.RowMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("%(title)s", style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextSecondary))
            }
        }
    }
}

@Composable
private fun OptionToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowDivider()
    ToggleRow(title = title, checked = checked, onCheckedChange = onChange, titleSize = 14.sp, minHeight = 52.dp)
}

@Composable
private fun SummaryBar(ui: DownloadUiState, onDownload: () -> Unit) {
    val format = ui.selectedFormat ?: return
    BottomActionBar {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val container = ui.state.container.uppercase()
                val summary = when {
                    format.sizeMb <= 0 && ui.state.kind == MediaKind.VIDEO ->
                        stringResource(R.string.download_summary_video_unknown, format.quality, container)
                    format.sizeMb <= 0 -> stringResource(R.string.download_summary_audio_unknown, container, format.quality)
                    ui.state.kind == MediaKind.VIDEO ->
                        stringResource(R.string.download_summary_video, format.quality, container, sizeLabel(format.sizeMb))
                    else -> stringResource(R.string.download_summary_audio, container, format.quality, sizeLabel(format.sizeMb))
                }
                Text(summary, style = BaseTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.download_folder), style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextMeta2))
            }
            PrimaryButton(
                text = stringResource(R.string.download_action),
                onClick = onDownload,
                icon = RecastIcons.Download,
            )
        }
    }
}

@Composable
private fun DownloadFormat.subtitle(): String = when {
    kind == MediaKind.VIDEO && detail.isNotEmpty() -> stringResource(R.string.download_format_video, codec, detail, container.uppercase())
    kind == MediaKind.VIDEO -> "$codec · ${container.uppercase()}"
    detail.isNotEmpty() -> "$codec · $detail"
    else -> codec
}

/** Tamanho em MB, ou "—" quando o site não informa. */
internal fun sizeLabel(sizeMb: Double): String = if (sizeMb > 0) Formatters.size(sizeMb * 1e6) else "—"

private fun FormatBadge.label(): Int = when (this) {
    FormatBadge.RECOMMENDED -> R.string.badge_recommended
    FormatBadge.BEST -> R.string.badge_best
}

// Previews ----------------------------------------------------------------------------------

private val previewClock = Clock.systemDefaultZone()

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun DownloadEmptyPreview() {
    RecastTheme {
        DownloadContent(
            ui = DownloadUiState(
                DownloadState(clipboardLink = "https://www.youtube.com/watch?v=la4_vectors"),
                AppSettings(),
                SampleData.recents(previewClock),
            ),
            clock = previewClock,
            actions = DownloadActions(),
        )
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun DownloadLoadingPreview() {
    RecastTheme {
        DownloadContent(
            ui = DownloadUiState(
                DownloadState(url = SampleData.DEFAULT_URL, inspectedUrl = SampleData.DEFAULT_URL, phase = InspectPhase.LOADING),
                AppSettings(),
                emptyList(),
            ),
            clock = previewClock,
            actions = DownloadActions(),
        )
    }
}

@Preview(widthDp = 396, heightDp = 1400, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun DownloadReadyPreview() {
    RecastTheme {
        DownloadContent(
            ui = DownloadUiState(
                DownloadState(
                    url = SampleData.DEFAULT_URL,
                    inspectedUrl = SampleData.DEFAULT_URL,
                    phase = InspectPhase.READY,
                    media = SampleData.mediaInfo(),
                    selectedFormatId = "v1080",
                    moreOpen = true,
                ),
                AppSettings(),
                emptyList(),
            ),
            clock = previewClock,
            actions = DownloadActions(),
        )
    }
}
