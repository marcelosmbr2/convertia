package com.recast.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.recast.app.R
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Momento relativo, já classificado; o texto sai de [text]. */
sealed interface TimeLabel {
    data object JustNow : TimeLabel
    data class MinutesAgo(val minutes: Long) : TimeLabel
    data class HoursAgo(val hours: Long) : TimeLabel
    /** Hoje, com a hora ("10:42"). */
    data class Today(val time: String) : TimeLabel
    data object Yesterday : TimeLabel
    /** Data curta ("24 out."). */
    data class Date(val date: String) : TimeLabel
}

/** Como o mockup escreve datas: "há 2 h", "Hoje, 10:42", "Ontem", "24 out.". */
object TimeLabels {
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", ptBr)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", ptBr)

    /**
     * Classifica [epochMillis] em relação a agora. Com [relative], horas de hoje viram
     * "há N h" (links recentes); sem, viram "Hoje, 10:42" (fila e histórico).
     */
    fun of(epochMillis: Long, clock: Clock, relative: Boolean): TimeLabel {
        val zone: ZoneId = clock.zone
        val instant = Instant.ofEpochMilli(epochMillis)
        val now = clock.instant()
        val elapsed = Duration.between(instant, now)
        val day = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(clock)
        return when {
            !elapsed.isNegative && elapsed < Duration.ofMinutes(1) -> TimeLabel.JustNow
            relative && !elapsed.isNegative && elapsed < Duration.ofHours(1) -> TimeLabel.MinutesAgo(elapsed.toMinutes())
            relative && day == today -> TimeLabel.HoursAgo(elapsed.toHours().coerceAtLeast(1))
            day == today -> TimeLabel.Today(timeFormat.format(instant.atZone(zone)))
            day == today.minusDays(1) -> TimeLabel.Yesterday
            else -> TimeLabel.Date(dateFormat.format(instant.atZone(zone)))
        }
    }
}

/** Texto do rótulo. [dayOnly] troca "Hoje, 10:42" por "Hoje" (idade na Biblioteca). */
@Composable
fun TimeLabel.text(dayOnly: Boolean = false, justNow: Int = R.string.time_now): String = when (this) {
    TimeLabel.JustNow -> stringResource(justNow)
    is TimeLabel.MinutesAgo -> stringResource(R.string.time_minutes_ago, minutes)
    is TimeLabel.HoursAgo -> stringResource(R.string.time_hours_ago, hours)
    is TimeLabel.Today -> if (dayOnly) stringResource(R.string.time_today) else stringResource(R.string.time_today_at, time)
    TimeLabel.Yesterday -> stringResource(R.string.time_yesterday)
    is TimeLabel.Date -> date
}
