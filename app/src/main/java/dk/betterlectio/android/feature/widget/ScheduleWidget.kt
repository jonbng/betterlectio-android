package dk.betterlectio.android.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import dk.betterlectio.android.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Glance home-screen widget showing today's schedule summary from SharedPreferences
 * (written by [ScheduleWidgetSnapshot] when schedule loads).
 */
class ScheduleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
            ?: context.getString(R.string.app_name)
        val lines = prefs.getString(KEY_LINES, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { listOf(context.getString(R.string.widget_open_app_hint)) }

        provideContent {
            GlanceTheme {
                ScheduleWidgetContent(title = title, lines = lines)
            }
        }
    }

    companion object {
        const val PREFS = "schedule_widget"
        const val KEY_TITLE = "title"
        const val KEY_LINES = "lines"
    }
}

@Composable
private fun ScheduleWidgetContent(title: String, lines: List<String>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
        Spacer(GlanceModifier.height(6.dp))
        lines.take(6).forEach { line ->
            Text(text = line, style = TextStyle(fontSize = 12.sp))
            Spacer(GlanceModifier.height(2.dp))
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

object ScheduleWidgetSnapshot {
    fun write(context: Context, dayLabel: String, eventLines: List<String>) {
        context.getSharedPreferences(ScheduleWidget.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ScheduleWidget.KEY_TITLE, dayLabel)
            .putString(ScheduleWidget.KEY_LINES, eventLines.joinToString("\n"))
            .apply()
        // Request widget refresh
        try {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            // Best-effort: update all instances asynchronously is complex; prefs update is enough
            // for next provideGlance. Trigger via broadcast.
            val intent = android.content.Intent(context, ScheduleWidgetReceiver::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun defaultDayLabel(context: Context, date: LocalDate = LocalDate.now()): String {
        val fmt = DateTimeFormatter.ofPattern("EEEE d. MMM", Locale.getDefault())
        return context.getString(R.string.widget_day_title, date.format(fmt))
    }
}
