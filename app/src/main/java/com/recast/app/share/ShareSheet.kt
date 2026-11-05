package com.recast.app.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recast.app.R
import com.recast.app.domain.Formatters
import com.recast.app.domain.MediaKind
import com.recast.app.ui.components.AppLogo
import com.recast.app.ui.components.DurationBadge
import com.recast.app.ui.components.ListCard
import com.recast.app.ui.components.PrimaryButton
import com.recast.app.ui.components.RadioRow
import com.recast.app.ui.components.RemoteThumbnail
import com.recast.app.ui.components.SecondaryButton
import com.recast.app.ui.components.StripedThumbnail
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.TextStyles

/** Sheet "Baixar este vídeo" aberto pelo menu Compartilhar. */
@Composable
fun ShareSheetContent(
    state: ShareState,
    onSelect: (String) -> Unit,
    onMoreFormats: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLogo(28.dp)
            Column {
                Text(stringResource(R.string.app_name), style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextMeta))
                Text(
                    stringResource(R.string.share_title),
                    style = BaseTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
                )
            }
        }

        when (state) {
            is ShareState.Ready -> {
                MediaRow(
                    title = state.media.title,
                    meta = listOf(state.media.channel, state.media.site).filter { it.isNotBlank() }.joinToString(" · "),
                    duration = state.media.duration.ifEmpty { null },
                    thumbnailUrl = state.media.thumbnailUrl,
                )
                ListCard {
                    state.options.forEachIndexed { index, option ->
                        RadioRow(
                            title = option.title(),
                            subtitle = option.subtitle(),
                            badge = if (option.recommended) stringResource(R.string.badge_recommended) else null,
                            selected = option.id == state.selectedId,
                            onClick = { onSelect(option.id) },
                            showDivider = index > 0,
                        )
                    }
                }
            }
            is ShareState.Loading -> {
                MediaRow(state.subject ?: state.url, state.url.removePrefix("https://").removePrefix("http://"), duration = null)
                StatusLine(stringResource(R.string.download_loading), Palette.TextPrimary)
            }
            is ShareState.Failed -> {
                MediaRow(state.url.removePrefix("https://").removePrefix("http://"), "", duration = null)
                StatusLine(stringResource(R.string.download_inspect_failed), Palette.DangerText)
            }
            ShareState.NoLink -> Unit
        }

        Text(
            stringResource(R.string.download_disclaimer),
            style = BaseTextStyle.copy(fontSize = 11.5.sp, lineHeight = 16.7.sp, color = Palette.TextMeta2),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = stringResource(R.string.share_more_formats),
                onClick = onMoreFormats,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.download_action),
                onClick = onDownload,
                enabled = state is ShareState.Ready,
                textStyle = TextStyles.Button.copy(fontSize = 14.sp),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MediaRow(title: String, meta: String, duration: String?, thumbnailUrl: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StripedThumbnail(Modifier.size(width = 96.dp, height = 54.dp), shape = RoundedCornerShape(8.dp), stripe = 6.dp) {
            RemoteThumbnail(thumbnailUrl)
            if (duration != null) DurationBadge(duration, fontSize = 10.sp, inset = 4.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = BaseTextStyle.copy(fontWeight = FontWeight.Medium, lineHeight = 19.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) Text(meta, style = TextStyles.RowMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusLine(text: String, dot: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text, style = BaseTextStyle.copy(fontSize = 13.sp, color = Palette.TextTertiary))
    }
}

@Composable
private fun QuickOption.title(): String =
    if (kind == MediaKind.VIDEO) stringResource(R.string.share_option_video, format.quality) else stringResource(R.string.share_option_audio)

/** "MP4 · H.264 · ≈ 412 MB", "MP3 · 320 kbps · ≈ 42,8 MB". */
@Composable
private fun QuickOption.subtitle(): String {
    val detail = if (kind == MediaKind.VIDEO) format.codec.substringBefore(" + ") else format.quality
    if (format.sizeMb <= 0) return "${container.uppercase()} · $detail"
    return stringResource(R.string.share_option_detail, container.uppercase(), detail, Formatters.size(format.sizeMb * 1e6))
}
