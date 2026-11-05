package com.recast.app.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recast.app.R
import com.recast.app.data.SampleData
import com.recast.app.domain.Formatters
import com.recast.app.domain.MediaKind
import com.recast.app.ui.TimeLabel
import com.recast.app.ui.TimeLabels
import com.recast.app.ui.components.BottomActionBar
import com.recast.app.ui.components.FileTypeTile
import com.recast.app.ui.components.IconCircleButton
import com.recast.app.ui.components.ListCard
import com.recast.app.ui.components.PillButton
import com.recast.app.ui.components.RowDivider
import com.recast.app.ui.components.ScreenHeader
import com.recast.app.ui.components.TextPillButton
import com.recast.app.ui.components.ThinProgressBar
import com.recast.app.ui.text
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.RecastIcons
import com.recast.app.ui.theme.RecastTheme
import com.recast.app.ui.theme.TabularFeatures
import com.recast.app.ui.theme.TextStyles
import java.time.Clock

/** Ações de cada linha da Fila. */
data class QueueActions(
    val onPause: (QueueRow) -> Unit = {},
    val onResume: (QueueRow) -> Unit = {},
    val onRetry: (QueueRow) -> Unit = {},
    val onRemove: (QueueRow) -> Unit = {},
    val onConvert: (jobId: Long) -> Unit = {},
    val onToggleAll: () -> Unit = {},
    val onClearDone: () -> Unit = {},
)

@Composable
fun QueueScreen(
    onConvertDownload: (Long) -> Unit,
    viewModel: QueueViewModel = viewModel(factory = QueueViewModel.Factory),
    clock: Clock = Clock.systemDefaultZone(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    QueueContent(
        overview = overview,
        clock = clock,
        actions = QueueActions(
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onRetry = viewModel::retry,
            onRemove = viewModel::remove,
            onConvert = onConvertDownload,
            onToggleAll = viewModel::toggleAll,
            onClearDone = viewModel::clearDone,
        ),
    )
}

@Composable
fun QueueContent(overview: QueueOverview, clock: Clock, actions: QueueActions) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.queue_title),
                subtitle = stringResource(
                    R.string.queue_counts,
                    pluralStringResource(R.plurals.queue_active, overview.activeCount, overview.activeCount),
                    pluralStringResource(R.plurals.queue_done, overview.doneCount, overview.doneCount),
                ),
            )
            overview.sections.forEach { section ->
                Section(section, overview.anyRunning, clock, actions, Modifier.padding(horizontal = 16.dp), overview.waitingForWifi)
            }
            if (overview.isEmpty) EmptyQueue()
        }
        StatusStrip(overview)
    }
}

@Composable
private fun Section(
    section: QueueSection,
    anyRunning: Boolean,
    clock: Clock,
    actions: QueueActions,
    modifier: Modifier,
    waitingForWifi: Boolean,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val title = when (section.kind) {
                SectionKind.IN_PROGRESS -> R.string.queue_section_active
                SectionKind.FAILED -> R.string.queue_section_failed
                SectionKind.DONE -> R.string.queue_section_done
            }
            Text(
                stringResource(title).uppercase(),
                style = TextStyles.SectionLabel.copy(color = Palette.TextSecondary),
                modifier = Modifier.semantics { heading() },
            )
            Box(
                Modifier
                    .heightIn(min = 20.dp)
                    .widthIn(min = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Raised3)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    section.rows.size.toString(),
                    style = BaseTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary),
                )
            }
            Box(Modifier.weight(1f))
            when (section.kind) {
                SectionKind.IN_PROGRESS -> TextPillButton(
                    text = stringResource(if (anyRunning) R.string.queue_pause_all else R.string.queue_resume_all),
                    onClick = actions.onToggleAll,
                )
                SectionKind.DONE -> TextPillButton(text = stringResource(R.string.queue_clear), onClick = actions.onClearDone)
                SectionKind.FAILED -> Unit
            }
        }
        ListCard {
            section.rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                QueueRowItem(row, clock, actions, waitingForWifi)
            }
        }
    }
}

@Composable
private fun QueueRowItem(row: QueueRow, clock: Clock, actions: QueueActions, waitingForWifi: Boolean) {
    Column(
        Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (row) {
                is QueueRow.Download -> {
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(shape)
                            .background(Palette.Raised2)
                            .border(1.dp, Palette.BorderSheet, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CvIcon(
                            if (row.job.request.kind == MediaKind.AUDIO) RecastIcons.Music else RecastIcons.Video,
                            contentDescription = null,
                            size = 17.dp,
                            tint = Palette.TextSecondary,
                            strokeWidth = 1.6f,
                        )
                    }
                }
                is QueueRow.Conversion -> FileTypeTile(row.item.ext, width = 36.dp, height = 36.dp, radius = 10.dp, bottomPadding = 12.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val title = when (row) {
                    is QueueRow.Download -> row.job.request.title
                    is QueueRow.Conversion -> row.item.name
                }
                Text(title, style = BaseTextStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    rowLine(row, clock, waitingForWifi),
                    style = BaseTextStyle.copy(fontSize = 12.sp, color = row.lineColor(), fontFeatureSettings = TabularFeatures),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RowActions(row, actions)
        }
        if (row.status != RowStatus.DONE) {
            val progress = if (row.status == RowStatus.FAILED) maxOf(row.progress, 3.0) else row.progress
            ThinProgressBar(
                progress = (progress / 100).toFloat(),
                color = row.barColor(),
                modifier = Modifier.padding(start = 48.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun RowActions(row: QueueRow, actions: QueueActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row is QueueRow.Download && row.status == RowStatus.DONE) {
            PillButton(
                onClick = { actions.onConvert(row.job.id) },
                height = 36.dp,
                background = Palette.Raised3,
                pressedBackground = Palette.BorderStrong,
                contentPadding = PaddingValues(horizontal = 12.dp),
                spacing = 6.dp,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                CvIcon(RecastIcons.Convert, contentDescription = null, size = 13.dp, strokeWidth = 2f)
                Text(stringResource(R.string.action_convert), style = TextStyles.Button.copy(fontSize = 12.5.sp))
            }
        }
        val canPause = row.status == RowStatus.ACTIVE || (row is QueueRow.Download && row.status == RowStatus.QUEUED)
        if (canPause) {
            IconCircleButton(RecastIcons.Pause, stringResource(R.string.queue_pause), { actions.onPause(row) }, strokeWidth = 2f)
        }
        if (row.status == RowStatus.PAUSED) {
            IconCircleButton(RecastIcons.Play, stringResource(R.string.queue_resume), { actions.onResume(row) })
        }
        if (row.status == RowStatus.FAILED) {
            IconCircleButton(RecastIcons.Retry, stringResource(R.string.queue_retry), { actions.onRetry(row) })
        }
        IconCircleButton(
            RecastIcons.Close,
            stringResource(R.string.queue_remove),
            { actions.onRemove(row) },
            iconSize = 15.dp,
            tint = Palette.TextMeta2,
        )
    }
}

@Composable
private fun rowLine(row: QueueRow, clock: Clock, waitingForWifi: Boolean): String = when (row) {
    is QueueRow.Download -> {
        val job = row.job
        val percent = job.progress.toInt()
        val total = job.sizeMb * 1e6
        when (row.status) {
            RowStatus.ACTIVE -> {
                val remainingMb = job.sizeMb * (100 - job.progress) / 100
                val remaining = if (job.speedMbps > 0 && job.sizeMb > 0) Formatters.minutesSeconds(remainingMb / job.speedMbps) else "–"
                stringResource(R.string.queue_download_active, percent, Formatters.decimal(job.speedMbps, 1), remaining)
            }
            RowStatus.QUEUED -> stringResource(
                if (waitingForWifi) R.string.queue_download_waiting_wifi else R.string.queue_download_queued,
            )
            RowStatus.PAUSED -> stringResource(
                R.string.queue_download_paused,
                percent,
                Formatters.size(total * job.progress / 100),
                Formatters.size(total),
            )
            RowStatus.FAILED -> job.error.orEmpty()
            RowStatus.DONE -> {
                val label = job.finishedAt?.let { TimeLabels.of(it, clock, relative = false) } ?: TimeLabel.JustNow
                stringResource(R.string.queue_download_done, label.text(justNow = R.string.time_just_now))
            }
        }
    }
    is QueueRow.Conversion -> {
        val item = row.item
        val percent = item.progress.toInt()
        when (row.status) {
            RowStatus.ACTIVE -> stringResource(R.string.queue_conversion_active, percent)
            RowStatus.QUEUED -> stringResource(R.string.queue_conversion_queued)
            RowStatus.PAUSED -> stringResource(R.string.queue_conversion_paused, percent)
            RowStatus.DONE -> stringResource(R.string.queue_conversion_done, Formatters.size(item.outputBytes), item.outputName)
            RowStatus.FAILED -> item.error.orEmpty()
        }
    }
}

private fun QueueRow.lineColor() = when (status) {
    RowStatus.ACTIVE, RowStatus.PAUSED -> Palette.TextEmphasis
    RowStatus.QUEUED -> Palette.TextSecondary
    RowStatus.FAILED -> Palette.DangerText
    RowStatus.DONE -> Palette.TextMeta
}

private fun QueueRow.barColor() = when (status) {
    RowStatus.ACTIVE -> Palette.TextPrimary
    RowStatus.QUEUED -> Palette.Gray3A
    RowStatus.PAUSED -> Palette.Gray6B
    RowStatus.FAILED -> Palette.Danger
    RowStatus.DONE -> Palette.TextSecondary
}

@Composable
private fun EmptyQueue() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.queue_empty_title), style = BaseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Text(
            stringResource(R.string.queue_empty_body),
            style = BaseTextStyle.copy(fontSize = 13.sp, lineHeight = 19.5.sp, color = Palette.TextMeta),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusStrip(overview: QueueOverview) {
    BottomActionBar {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (overview.isIdle) Palette.Gray55 else Palette.TextPrimary),
            )
            val parts = buildList {
                if (overview.downloadingCount > 0) {
                    add(
                        pluralStringResource(
                            R.plurals.queue_downloading,
                            overview.downloadingCount,
                            overview.downloadingCount,
                            Formatters.decimal(overview.totalSpeedMbps, 1),
                        ),
                    )
                }
                if (overview.convertingCount > 0) {
                    add(pluralStringResource(R.plurals.queue_converting, overview.convertingCount, overview.convertingCount))
                }
            }
            Text(
                when {
                    parts.isNotEmpty() -> parts.joinToString(" · ")
                    overview.waitingForWifi -> stringResource(R.string.queue_waiting_wifi)
                    else -> stringResource(R.string.queue_idle)
                },
                style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextTertiary, fontFeatureSettings = TabularFeatures),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(widthDp = 396, heightDp = 1100, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun QueuePreview() {
    val clock = Clock.systemDefaultZone()
    RecastTheme {
        QueueContent(
            overview = QueueOverview.build(SampleData.downloadJobs(clock), SampleData.conversionItems(), conversionsRunning = true),
            clock = clock,
            actions = QueueActions(),
        )
    }
}

@Preview(widthDp = 396, heightDp = 600, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun QueueEmptyPreview() {
    RecastTheme {
        QueueContent(QueueOverview.build(emptyList(), emptyList(), false), Clock.systemDefaultZone(), QueueActions())
    }
}
