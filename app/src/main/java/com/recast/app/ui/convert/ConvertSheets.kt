package com.recast.app.ui.convert

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recast.app.R
import com.recast.app.domain.AudioBitrate
import com.recast.app.domain.AudioChannels
import com.recast.app.domain.CompressionLevel
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionOptions
import com.recast.app.domain.ConversionStatus
import com.recast.app.domain.FormatCatalog
import com.recast.app.domain.Formatters
import com.recast.app.domain.ImageResize
import com.recast.app.domain.LibraryItem
import com.recast.app.domain.MediaCategory
import com.recast.app.domain.OutputFormat
import com.recast.app.domain.QualityPreset
import com.recast.app.domain.SizeEstimator
import com.recast.app.domain.VideoCodec
import com.recast.app.domain.VideoResolution
import com.recast.app.ui.AppSheet
import com.recast.app.ui.FormatTexts
import com.recast.app.ui.components.ChoiceChip
import com.recast.app.ui.components.FileTypeTile
import com.recast.app.ui.components.IconCircleButton
import com.recast.app.ui.components.ListCard
import com.recast.app.ui.components.PillTextField
import com.recast.app.ui.components.PrimaryButton
import com.recast.app.ui.components.RowDivider
import com.recast.app.ui.components.SecondaryButton
import com.recast.app.ui.components.SectionLabel
import com.recast.app.ui.components.SegmentedControl
import com.recast.app.ui.components.TextPillButton
import com.recast.app.ui.components.ToggleRow
import com.recast.app.ui.descriptionText
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.CvIconSpec
import com.recast.app.ui.theme.MonoTextStyle
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.RecastIcons
import com.recast.app.ui.theme.TextStyles

// Adicionar arquivos ------------------------------------------------------------------------

@Composable
fun ColumnScope.AddFilesSheet(
    recentDownloads: List<LibraryItem>,
    onPicked: (files: List<PickedFile>, location: String) -> Unit,
    onAddDownload: (String) -> Unit,
) {
    val context = LocalContext.current
    val galleryLocation = stringResource(R.string.add_location_gallery)
    val filesLocation = stringResource(R.string.add_location_files)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        onPicked(resolvePickedFiles(context, uris, persist = false), galleryLocation)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onPicked(resolvePickedFiles(context, uris, persist = true), filesLocation)
    }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.add_title), style = TextStyles.SheetTitle)
        ListCard {
            AddSourceRow(
                icon = RecastIcons.Image,
                tint = Palette.GalleryTint,
                tileAlpha = 0.1f,
                title = stringResource(R.string.add_gallery),
                description = stringResource(R.string.add_gallery_desc),
                onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
            )
            RowDivider()
            AddSourceRow(
                icon = RecastIcons.Folder,
                tint = Palette.FilesTint,
                tileAlpha = 0.08f,
                title = stringResource(R.string.add_files),
                description = stringResource(R.string.add_files_desc),
                onClick = { documents.launch(arrayOf("*/*")) },
            )
        }
        if (recentDownloads.isNotEmpty()) {
            SectionLabel(stringResource(R.string.add_from_downloads), Modifier.padding(top = 4.dp))
            ListCard {
                recentDownloads.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clickable(role = Role.Button) { onAddDownload(item.id) }
                            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = BaseTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${item.format} · ${item.detail} · ${Formatters.size(item.sizeBytes)}",
                                style = TextStyles.RowMeta,
                                maxLines = 1,
                            )
                        }
                        CvIcon(RecastIcons.Plus, contentDescription = null, size = 18.dp, tint = Palette.TextMeta, strokeWidth = 1.9f)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourceRow(
    icon: CvIconSpec,
    tint: Color,
    tileAlpha: Float,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = tileAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            CvIcon(icon, contentDescription = null, size = 19.dp, tint = tint, strokeWidth = 1.7f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyles.RowTitle)
            Text(description, style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary))
        }
    }
}

/** Nome e tamanho dos arquivos escolhidos; com [persist], guarda o acesso para converter depois. */
private fun resolvePickedFiles(context: Context, uris: List<Uri>, persist: Boolean): List<PickedFile> =
    uris.mapNotNull { uri ->
        if (persist) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        context.contentResolver.queryNameAndSize(uri)?.let { (name, size) -> PickedFile(uri.toString(), name, size) }
    }

private fun ContentResolver.queryNameAndSize(uri: Uri): Pair<String, Long>? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: return@use null
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
        name to size
    }
}.getOrNull()

// Inspetor ----------------------------------------------------------------------------------

/** Ações do inspetor, ligadas ao ConvertViewModel e ao AppViewModel. */
data class InspectorActions(
    val onPickFormat: () -> Unit,
    val onOptions: ((ConversionOptions) -> ConversionOptions) -> Unit,
    val onApplyToAll: () -> Unit,
    val onRemove: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun ColumnScope.InspectorSheet(item: ConversionItem, actions: InspectorActions) {
    val target = item.target
    val options = item.options
    val lossless = FormatCatalog.isLossless(target.ext)
    val editable = item.status != ConversionStatus.CONVERTING

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileTypeTile(item.ext, width = 40.dp, height = 50.dp, radius = 8.dp, fontSize = 9.sp, bottomPadding = 6.dp, borderAlpha = 0.3f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = BaseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp))
                Text(
                    "${Formatters.size(item.sizeBytes)} · ${stringResource(FormatTexts.categorySingular(item.category))} ${item.ext.uppercase()}",
                    style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
                )
            }
        }

        Field(stringResource(R.string.inspector_output)) {
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .alpha(if (editable) 1f else 0.5f)
                    .clip(shape)
                    .background(Palette.Raised2)
                    .border(1.dp, Palette.BorderStrong, shape)
                    .clickable(enabled = editable, role = Role.Button, onClick = actions.onPickFormat)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(target.ext, style = BaseTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    target.descriptionText(),
                    style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CvIcon(RecastIcons.ChevronDown, contentDescription = null, size = 16.dp, tint = Palette.TextMeta, strokeWidth = 2f)
            }
        }

        val hasQuality = !lossless && target.category in setOf(MediaCategory.IMAGE, MediaCategory.VIDEO, MediaCategory.AUDIO)
        if (hasQuality) QualityField(options, actions.onOptions)

        when (target.category) {
            MediaCategory.VIDEO -> {
                val codecs = FormatCatalog.codecsFor(target.ext)
                if (codecs.isNotEmpty()) {
                    SegmentedField(stringResource(R.string.inspector_codec), codecs, options.codec, { it.label() }) { v ->
                        actions.onOptions { it.copy(codec = v) }
                    }
                }
                SegmentedField(stringResource(R.string.inspector_resolution), VideoResolution.entries, options.resolution, { it.label() }) { v ->
                    actions.onOptions { it.copy(resolution = v) }
                }
            }
            MediaCategory.AUDIO -> {
                if (!lossless) {
                    SegmentedField(stringResource(R.string.inspector_bitrate), AudioBitrate.entries, options.bitrate, { it.label() }) { v ->
                        actions.onOptions { it.copy(bitrate = v) }
                    }
                }
                SegmentedField(stringResource(R.string.inspector_channels), AudioChannels.entries, options.channels, { it.label() }) { v ->
                    actions.onOptions { it.copy(channels = v) }
                }
            }
            MediaCategory.IMAGE -> {
                SegmentedField(stringResource(R.string.inspector_resize), ImageResize.entries, options.resize, { it.label() }) { v ->
                    actions.onOptions { it.copy(resize = v) }
                }
                InspectorToggle(R.string.inspector_strip, R.string.inspector_strip_desc, options.stripMetadata) { on ->
                    actions.onOptions { it.copy(stripMetadata = on) }
                }
            }
            MediaCategory.DOCUMENT -> {
                if (target.ext == "PDF") {
                    InspectorToggle(R.string.inspector_compress, R.string.inspector_compress_desc, options.compressImages) { on ->
                        actions.onOptions { it.copy(compressImages = on) }
                    }
                }
                // OCR: imagem digitalizada → PDF pesquisável, ou PDF sem texto → documento.
                if (item.ocrAvailable && (item.category == MediaCategory.IMAGE && target.ext == "PDF" || item.ext == "pdf")) {
                    InspectorToggle(R.string.inspector_ocr, R.string.inspector_ocr_desc, options.ocr) { on ->
                        actions.onOptions { it.copy(ocr = on) }
                    }
                }
            }
            MediaCategory.ARCHIVE -> {
                if (target.ext != "TAR") {
                    SegmentedField(stringResource(R.string.inspector_compression), CompressionLevel.entries, options.compressionLevel, { it.label() }) { v ->
                        actions.onOptions { it.copy(compressionLevel = v) }
                    }
                }
            }
            MediaCategory.EBOOK -> Unit
        }
    }

    // Rodapé fixo.
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(Palette.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            val done = item.status == ConversionStatus.DONE
            Text(
                stringResource(if (done) R.string.inspector_final_size else R.string.inspector_estimated_size),
                style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextMeta),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (done) Formatters.size(item.outputBytes) else "≈ " + Formatters.size(SizeEstimator.estimatedBytes(item)),
                style = BaseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.inspector_apply_all, stringResource(FormatTexts.categoryAll(item.category))),
            onClick = actions.onApplyToAll,
            height = 44.dp,
            textStyle = TextStyles.Button.copy(fontSize = 13.5.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPillButton(
                text = stringResource(R.string.inspector_remove),
                onClick = actions.onRemove,
                color = Palette.DangerText,
                fontSize = 14.sp,
                height = 48.dp,
                horizontalPadding = 18.dp,
            )
            PrimaryButton(
                text = stringResource(R.string.inspector_done),
                onClick = actions.onDone,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = FieldLabelStyle)
        content()
    }
}

private val FieldLabelStyle = BaseTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Palette.TextMeta)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityField(options: ConversionOptions, onOptions: ((ConversionOptions) -> ConversionOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text(stringResource(R.string.inspector_quality), style = FieldLabelStyle, modifier = Modifier.weight(1f))
            Text("${options.quality}%", style = MonoTextStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Palette.TextEmphasis))
        }
        SegmentedControl(
            options = QualityPreset.choices,
            selected = options.preset.takeIf { it != QualityPreset.CUSTOM },
            onSelect = { preset -> onOptions { it.withPreset(preset) } },
            label = { stringResource(it.label()) },
        )
        val colors = SliderDefaults.colors(
            thumbColor = Palette.Accent,
            activeTrackColor = Palette.Accent,
            inactiveTrackColor = Palette.BorderStrong,
        )
        Slider(
            value = options.quality.toFloat(),
            onValueChange = { value -> onOptions { it.withQuality(Formatters.roundHalfUp(value.toDouble()).toInt()) } },
            valueRange = 10f..100f,
            colors = colors,
            thumb = {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Palette.Accent))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                )
            },
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
private fun <T> SegmentedField(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Field(label) {
        SegmentedControl(options = options, selected = selected, onSelect = onSelect, label = text)
    }
}

@Composable
private fun InspectorToggle(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(
        title = stringResource(title),
        description = stringResource(description),
        checked = checked,
        onCheckedChange = onChange,
        titleSize = 14.sp,
        minHeight = 0.dp,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
    )
}

@Composable
private fun QualityPreset.label(): Int = when (this) {
    QualityPreset.SMALLER -> R.string.quality_smaller
    QualityPreset.BALANCED -> R.string.quality_balanced
    QualityPreset.BEST -> R.string.quality_best
    QualityPreset.CUSTOM -> R.string.quality_custom
}

@Composable
private fun VideoCodec.label(): String = when (this) {
    VideoCodec.H264 -> "H.264"
    VideoCodec.HEVC -> "HEVC"
    VideoCodec.VP9 -> "VP9"
    VideoCodec.AV1 -> "AV1"
}

@Composable
private fun VideoResolution.label(): String = when (this) {
    VideoResolution.ORIGINAL -> stringResource(R.string.option_original)
    VideoResolution.P1080 -> "1080p"
    VideoResolution.P720 -> "720p"
}

@Composable
private fun AudioBitrate.label(): String = when (this) {
    AudioBitrate.KBPS_128 -> "128 kbps"
    AudioBitrate.KBPS_192 -> "192 kbps"
    AudioBitrate.KBPS_320 -> "320 kbps"
}

@Composable
private fun AudioChannels.label(): String = when (this) {
    AudioChannels.STEREO -> stringResource(R.string.option_stereo)
    AudioChannels.MONO -> stringResource(R.string.option_mono)
}

@Composable
private fun ImageResize.label(): String = when (this) {
    ImageResize.ORIGINAL -> stringResource(R.string.option_original)
    ImageResize.HALF -> "50%"
    ImageResize.MAX_2560 -> "2560 px"
}

@Composable
private fun CompressionLevel.label(): String = when (this) {
    CompressionLevel.FAST -> stringResource(R.string.option_level_fast)
    CompressionLevel.NORMAL -> stringResource(R.string.option_level_normal)
    CompressionLevel.MAX -> stringResource(R.string.option_level_max)
}

// Seletor de formato ------------------------------------------------------------------------

data class PickerActions(
    val onBack: () -> Unit,
    val onQuery: (String) -> Unit,
    val onCategory: (MediaCategory) -> Unit,
    val onChoose: (OutputFormat) -> Unit,
)

private data class PickerTile(val format: OutputFormat, val description: String, val current: Boolean, val disabled: Boolean)

@Composable
fun ColumnScope.FormatPickerSheet(
    sheet: AppSheet.FormatPicker,
    items: List<ConversionItem>,
    actions: PickerActions,
) {
    val item = sheet.itemId?.let { id -> items.firstOrNull { it.id == id } }
    // Formatos possíveis: os do arquivo, ou a união dos arquivos editáveis ("Tudo para").
    val available: Set<OutputFormat> = if (item != null) {
        item.targets.toSet()
    } else {
        items.filter { it.status != ConversionStatus.CONVERTING }.flatMap { it.targets }.toSet()
    }
    val categories = available.map { it.category }.distinct()
    val query = sheet.query.trim().lowercase()
    val currentLabel = stringResource(R.string.picker_current)
    val categoryLabels = MediaCategory.entries.associateWith { stringResource(FormatTexts.categoryLabel(it)) }
    // O próprio formato do arquivo aparece desabilitado como "Formato atual".
    fun shown(format: OutputFormat) = format in available || (item != null && FormatCatalog.isSameFormat(item.ext, format.ext))
    val formats = (if (query.isEmpty()) listOf(sheet.category) else categories)
        .flatMap { category -> FormatCatalog.outputs.getValue(category).map { OutputFormat(it, category) } }
        .filter(::shown)
    val tiles = formats.map { format ->
        PickerTile(format, format.descriptionText(), current = false, disabled = false)
    }.filter { tile ->
        query.isEmpty() || tile.format.ext.lowercase().contains(query) || tile.description.lowercase().contains(query)
    }.map { tile ->
        val current = item != null && tile.format == item.target
        val disabled = tile.format !in available
        val description = when {
            disabled -> currentLabel
            query.isNotEmpty() -> "${categoryLabels.getValue(tile.format.category)} · ${tile.description}"
            else -> tile.description
        }
        tile.copy(description = description, current = current, disabled = disabled)
    }

    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            // A seta fica 10 dp para fora, como no mockup (mais a folga da área de toque de 48 dp).
            modifier = if (sheet.backToInspector) Modifier.offset(x = (-14).dp) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sheet.backToInspector) {
                IconCircleButton(
                    icon = RecastIcons.ArrowLeft,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = actions.onBack,
                    iconSize = 20.dp,
                    tint = Palette.TextPrimary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.picker_title), style = TextStyles.SheetTitle)
                Text(
                    item?.name ?: stringResource(R.string.picker_all_files),
                    style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PillTextField(
            value = sheet.query,
            onValueChange = actions.onQuery,
            placeholder = stringResource(R.string.picker_search),
            leadingIconSize = 16.dp,
            height = 44.dp,
            radius = 22.dp,
            background = Palette.Raised2,
            border = null,
        )
    }
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            ChoiceChip(
                text = categoryLabels.getValue(category),
                selected = query.isEmpty() && category == sheet.category,
                onClick = { actions.onCategory(category) },
                height = 34.dp,
                minWidth = 0.dp,
                fontWeight = FontWeight.Medium,
                unselectedBackground = Color.Transparent,
                unselectedText = Palette.TextSecondary,
            )
        }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Text(
            (if (query.isEmpty()) categoryLabels.getValue(sheet.category) else stringResource(R.string.picker_results)).uppercase(),
            style = TextStyles.SectionLabel,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
        )
        if (tiles.isEmpty()) {
            Text(
                stringResource(R.string.picker_empty),
                style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextTertiary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        tiles.chunked(3).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> FormatTile(tile, onClick = { actions.onChoose(tile.format) }, modifier = Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FormatTile(tile: PickerTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(64.dp)
            .alpha(if (tile.disabled) 0.4f else 1f)
            .clip(shape)
            .background(if (tile.current) Palette.Selected else Palette.Raised2)
            .border(1.dp, if (tile.current) Palette.TextPrimary else Palette.BorderStrong, shape)
            .clickable(enabled = !tile.disabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(tile.format.ext, style = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold))
        Text(
            tile.description,
            style = BaseTextStyle.copy(fontSize = 11.sp, color = Palette.TextTertiary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
