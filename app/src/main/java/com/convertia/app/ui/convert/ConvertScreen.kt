package com.convertia.app.ui.convert

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.convertia.app.R
import com.convertia.app.data.SampleData
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.Formatters
import com.convertia.app.domain.Preset
import com.convertia.app.ui.components.BottomActionBar
import com.convertia.app.ui.components.FileTypeTile
import com.convertia.app.ui.components.IconCircleButton
import com.convertia.app.ui.components.PillButton
import com.convertia.app.ui.components.PrimaryButton
import com.convertia.app.ui.components.ScreenHeader
import com.convertia.app.ui.components.SectionLabel
import com.convertia.app.ui.components.TextPillButton
import com.convertia.app.ui.components.ThinProgressBar
import com.convertia.app.ui.components.dotGrid
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.ConvertiaIcons
import com.convertia.app.ui.theme.ConvertiaTheme
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.MonoTextStyle
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TabularFeatures
import com.convertia.app.ui.theme.TextStyles

/** Navegação que a tela pede ao AppViewModel (sheets). */
data class ConvertNavigation(
    val openItem: (String) -> Unit = {},
    val openPicker: (itemId: String?) -> Unit = {},
    val openAddFiles: () -> Unit = {},
)

@Composable
fun ConvertScreen(
    navigation: ConvertNavigation,
    viewModel: ConvertViewModel = viewModel(factory = ConvertViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    ConvertContent(
        ui = ui,
        navigation = navigation,
        onPreset = viewModel::applyPreset,
        onClearDone = viewModel::clearDone,
        onPrimary = viewModel::onPrimary,
    )
}

@Composable
fun ConvertContent(
    ui: ConvertUiState,
    navigation: ConvertNavigation,
    onPreset: (Preset) -> Unit,
    onClearDone: () -> Unit,
    onPrimary: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.convert_title)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CvIcon(ConvertiaIcons.Lock, contentDescription = null, size = 13.dp, tint = Palette.TextSecondary, strokeWidth = 1.9f)
                    val summary = if (ui.items.isEmpty()) {
                        stringResource(R.string.convert_summary_empty)
                    } else {
                        pluralStringResource(R.plurals.convert_summary, ui.items.size, ui.items.size, Formatters.size(ui.totalBytes))
                    }
                    Text(summary, style = TextStyles.ScreenSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            QuickPresets(ui.quickPresets, onPreset)
            if (ui.items.isEmpty()) {
                EmptyState(onAdd = navigation.openAddFiles, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                FileList(ui, navigation, onClearDone, Modifier.padding(horizontal = 16.dp))
            }
        }
        if (ui.items.isNotEmpty()) {
            ActionBar(ui.primary, onAdd = navigation.openAddFiles, onPrimary = onPrimary)
        }
    }
}

@Composable
private fun QuickPresets(presets: List<Preset>, onPreset: (Preset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.convert_quick_presets), Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                PillButton(
                    onClick = { onPreset(preset) },
                    height = 40.dp,
                    shape = RoundedCornerShape(12.dp),
                    background = Palette.Card,
                    pressedBackground = Palette.Raised,
                    border = BorderStroke(1.dp, Palette.BorderStrong),
                ) {
                    Text(preset.sources.first().uppercase(), style = MonoTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextMeta))
                    CvIcon(ConvertiaIcons.ArrowRight, contentDescription = null, size = 12.dp, tint = Palette.TextMuted, strokeWidth = 2f)
                    Text(preset.target.ext, style = MonoTextStyle.copy(fontSize = 12.5.sp))
                }
            }
        }
    }
}

@Composable
private fun FileList(ui: ConvertUiState, navigation: ConvertNavigation, onClearDone: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.convert_files_label, ui.items.size).uppercase(),
                style = TextStyles.SectionLabel,
                modifier = Modifier.weight(1f),
            )
            if (ui.hasDone) {
                TextPillButton(
                    text = stringResource(R.string.convert_clear),
                    onClick = onClearDone,
                    color = Palette.TextSecondary,
                    fontWeight = FontWeight.Medium,
                    horizontalPadding = 10.dp,
                )
            }
            if (ui.hasPickable) {
                PillButton(
                    onClick = { navigation.openPicker(null) },
                    height = 36.dp,
                    shape = RoundedCornerShape(10.dp),
                    background = Palette.Raised,
                    pressedBackground = Palette.Raised2,
                    border = BorderStroke(1.dp, Palette.BorderStrong),
                    contentColor = Palette.TextSecondary,
                    contentPadding = PaddingValues(start = 12.dp, end = 10.dp),
                    spacing = 6.dp,
                ) {
                    Text(
                        buildAnnotatedString {
                            append(stringResource(R.string.convert_all_to))
                            append(' ')
                            withStyle(SpanStyle(color = Palette.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                                append(ui.commonTarget ?: stringResource(R.string.convert_various))
                            }
                        },
                        style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextSecondary),
                        maxLines = 1,
                    )
                    CvIcon(ConvertiaIcons.ChevronDown, contentDescription = null, size = 14.dp, strokeWidth = 2f)
                }
            }
        }
        ui.items.forEach { item ->
            FileCard(
                item = item,
                running = ui.running,
                etaSeconds = ui.eta[item.id],
                onOpen = { navigation.openItem(item.id) },
                onPick = { navigation.openPicker(item.id) },
            )
        }
    }
}

@Composable
private fun FileCard(
    item: ConversionItem,
    running: Boolean,
    etaSeconds: Int?,
    onOpen: () -> Unit,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, Palette.Border, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileTypeTile(item.ext, width = 36.dp, height = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = BaseTextStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.size(item.sizeBytes)} · ${item.location}",
                    style = TextStyles.RowMeta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.status != ConversionStatus.CONVERTING) {
                PillButton(
                    onClick = onPick,
                    height = 36.dp,
                    shape = RoundedCornerShape(10.dp),
                    background = Palette.Raised2,
                    pressedBackground = Palette.Raised3,
                    border = BorderStroke(1.dp, Palette.BorderStrong),
                    contentPadding = PaddingValues(start = 10.dp, end = 8.dp),
                    spacing = 4.dp,
                ) {
                    CvIcon(ConvertiaIcons.ArrowRight, contentDescription = null, size = 12.dp, tint = Palette.TextMeta2, strokeWidth = 2f)
                    Text(item.target.ext, style = BaseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    CvIcon(ConvertiaIcons.ChevronDown, contentDescription = null, size = 14.dp, tint = Palette.TextMeta, strokeWidth = 2f)
                }
            } else {
                Text(
                    "→ ${item.target.ext}",
                    style = BaseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary),
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
        when {
            item.status == ConversionStatus.CONVERTING -> Column(
                Modifier.padding(start = 48.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row {
                    val label = if (running) {
                        stringResource(R.string.convert_converting_eta, etaSeconds ?: 1)
                    } else {
                        stringResource(R.string.convert_paused)
                    }
                    Text(
                        label,
                        style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextEmphasis, fontFeatureSettings = TabularFeatures),
                        modifier = Modifier.weight(1f),
                    )
                    Text("${item.progress.toInt()}%", style = MonoTextStyle.copy(fontSize = 12.sp, color = Palette.TextMeta))
                }
                ThinProgressBar((item.progress / 100).toFloat(), color = if (running) Palette.TextPrimary else Palette.Gray5A)
            }
            item.status == ConversionStatus.DONE -> Row(
                Modifier.padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CvIcon(ConvertiaIcons.CheckThin, contentDescription = null, size = 15.dp, tint = Palette.TextSecondary, strokeWidth = 2.2f)
                Text(Formatters.size(item.outputBytes), style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextEmphasis))
                Text(
                    Formatters.sizeDelta(item.sizeBytes, item.outputBytes),
                    style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextTertiary),
                )
            }
            running -> Text(
                stringResource(R.string.convert_queued),
                style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.BackgroundEmpty)
            .border(1.dp, Palette.DividerStrong, shape)
            .dotGrid(centerX = 0.5f, centerY = 0.3f, radiusX = 0.7f, radiusY = 0.6f, innerAlpha = 1f, solidUntil = 0.3f, fadeEnd = 1f)
            .drawBehind {
                // Brilho laranja no topo (220 px, começando 40 px acima).
                val radius = 110.dp.toPx()
                val center = Offset(size.width / 2, -40.dp.toPx() + radius)
                drawCircle(
                    Brush.radialGradient(
                        0f to Palette.Accent.copy(alpha = 0.18f),
                        0.65f to Palette.Accent.copy(alpha = 0f),
                        center = center,
                        radius = radius,
                    ),
                    radius,
                    center,
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .height(84.dp)
                .padding(bottom = 0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            StackedTile("JPG", rotation = -14f, dy = 7.dp, highlighted = false)
            StackedTile("PDF", rotation = 0f, dy = 0.dp, highlighted = true, modifier = Modifier.zIndex(2f))
            StackedTile("MP3", rotation = 14f, dy = 7.dp, highlighted = false)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.convert_empty_title),
            style = BaseTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        )
        Text(
            stringResource(R.string.convert_empty_body),
            style = BaseTextStyle.copy(fontSize = 13.5.sp, lineHeight = 20.sp, color = Palette.TextSecondary),
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.convert_choose_files),
            onClick = onAdd,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StackedTile(label: String, rotation: Float, dy: Dp, highlighted: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 0.dp)
            .offset(y = dy)
            .rotate(rotation)
            .size(width = 56.dp, height = 70.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (highlighted) Palette.Raised2 else Palette.Card)
            .border(1.dp, if (highlighted) Palette.Accent else Palette.BorderStrong, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(width = 16.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(if (highlighted) Palette.Accent else Palette.Gray33))
        Text(
            label,
            style = BaseTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (highlighted) Palette.TextPrimary else Palette.TextSecondary),
        )
    }
}

@Composable
private fun ActionBar(primary: PrimaryAction, onAdd: () -> Unit, onPrimary: () -> Unit) {
    BottomActionBar {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconCircleButton(
                icon = ConvertiaIcons.Plus,
                contentDescription = stringResource(R.string.convert_add_files),
                onClick = onAdd,
                size = 48.dp,
                iconSize = 20.dp,
                strokeWidth = 1.9f,
                tint = Palette.TextPrimary,
                background = Palette.Raised,
                border = BorderStroke(1.dp, Palette.BorderStrong),
            )
            val label = when (primary) {
                PrimaryAction.Pause -> stringResource(R.string.convert_pause)
                PrimaryAction.Resume -> stringResource(R.string.convert_resume)
                is PrimaryAction.Convert -> pluralStringResource(R.plurals.convert_n_files, primary.count, primary.count)
                PrimaryAction.Idle -> stringResource(R.string.convert_action)
            }
            PrimaryButton(
                text = label,
                onClick = onPrimary,
                icon = if (primary == PrimaryAction.Pause) ConvertiaIcons.PauseFilled else ConvertiaIcons.PlayFilledWide,
                iconSize = 14.dp,
                enabled = primary != PrimaryAction.Idle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun ConvertPreview() {
    ConvertiaTheme {
        ConvertContent(
            ui = ConvertUiState(SampleData.conversionItems(), running = true, eta = mapOf("sample-file-2" to 12), quickPresets = SampleData.presets.take(4)),
            navigation = ConvertNavigation(),
            onPreset = {},
            onClearDone = {},
            onPrimary = {},
        )
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun ConvertEmptyPreview() {
    ConvertiaTheme {
        ConvertContent(ConvertUiState(quickPresets = SampleData.presets.take(4)), ConvertNavigation(), {}, {}, {})
    }
}
