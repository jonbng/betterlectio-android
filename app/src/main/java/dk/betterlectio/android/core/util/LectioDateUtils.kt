package dk.betterlectio.android.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object LectioDateUtils {
    /** Lectio HTML / API parsing stays on a fixed Danish locale (server language). */
    private val parseLocale = Locale.forLanguageTag("da-DK")
    private val dMy = DateTimeFormatter.ofPattern("d/M-yyyy", parseLocale)
    private val dM = DateTimeFormatter.ofPattern("d/M", parseLocale)
    private val hm = DateTimeFormatter.ofPattern("HH:mm", parseLocale)
    private val dMyHm = DateTimeFormatter.ofPattern("d/M-yyyy HH:mm", parseLocale)

    /** Active app locale for user-facing date labels. */
    private fun displayLocale(): Locale = Locale.getDefault()

    fun isoWeek(date: LocalDate = LocalDate.now()): Int =
        date.get(WeekFields.ISO.weekOfWeekBasedYear())

    fun isoWeekYear(date: LocalDate = LocalDate.now()): Int =
        date.get(WeekFields.ISO.weekBasedYear())

    fun weekStart(year: Int, week: Int): LocalDate =
        LocalDate.of(year, 1, 4)
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(DayOfWeek.MONDAY)

    fun parseLectioDate(raw: String, defaultYear: Int = LocalDate.now().year): LocalDateTime? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        // dd-MM-yyyy HH:mm:ss
        listOf(
            "d/M-yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "d/M-yyyy",
            "d/M HH:mm",
            "d/M",
            "HH:mm",
        ).forEach { pattern ->
            try {
                val fmt = DateTimeFormatter.ofPattern(pattern, parseLocale)
                return when {
                    pattern.contains("HH") && pattern.contains("yyyy") ->
                        LocalDateTime.parse(t, fmt)
                    pattern.contains("HH") && pattern.contains("M") && !pattern.contains("y") -> {
                        val dt = LocalDateTime.parse("$t", DateTimeFormatter.ofPattern("d/M HH:mm", parseLocale))
                            .withYear(defaultYear)
                        dt
                    }
                    pattern == "HH:mm" -> LocalDateTime.of(LocalDate.now(), LocalTime.parse(t, hm))
                    pattern.contains("y") -> LocalDate.parse(t, fmt).atStartOfDay()
                    else -> LocalDate.parse(t, fmt).withYear(defaultYear).atStartOfDay()
                }
            } catch (_: Exception) {
                // try next
            }
        }
        // "i dag 12:00" / weekday forms — best effort
        val todayMatch = Regex("""i dag\s+(\d{1,2}:\d{2})""", RegexOption.IGNORE_CASE).find(t)
        if (todayMatch != null) {
            val time = LocalTime.parse(todayMatch.groupValues[1], hm)
            return LocalDateTime.of(LocalDate.now(), time)
        }
        return null
    }

    fun parseTimeRange(line: String): Pair<LocalTime, LocalTime>? {
        val times = Regex("""(\d{1,2}:\d{2})""").findAll(line).map { it.groupValues[1] }.toList()
        if (times.size < 2) return null
        return try {
            LocalTime.parse(times[0], hm) to LocalTime.parse(times[1], hm)
        } catch (_: Exception) {
            null
        }
    }

    fun formatHm(time: LocalTime): String = time.format(hm)

    fun formatShortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE d/M", displayLocale()))
}
