package dk.betterlectio.android.feature.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dk.betterlectio.android.MainActivity
import dk.betterlectio.android.R
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import dk.betterlectio.android.feature.schedule.timeLabel
import dk.betterlectio.android.feature.settings.SettingsStore
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android stand-in for iOS Live Activity: ongoing notification during the current lesson,
 * with next-boundary big text and open-app action.
 */
@Singleton
class LiveLessonNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val notifId = 42

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.live_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    fun update(events: List<ScheduleEvent>, now: LocalDateTime = LocalDateTime.now()) {
        val current = LiveLessonBoundary.currentLesson(events, now)
        val next = LiveLessonBoundary.nextBoundary(events, now)

        if (current == null && next == null) {
            nm.cancel(notifId)
            return
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setOngoing(current != null)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setAutoCancel(current == null)

        if (current != null) {
            val remaining = ChronoUnit.MINUTES.between(now, current.end).coerceAtLeast(0)
            val minsLeft = context.getString(R.string.live_minutes_left, remaining.toInt())
            val subject = friendlyTitle(current)
            val text = if (current.room.isNullOrBlank()) {
                "$subject · $minsLeft"
            } else {
                "$subject · ${current.room} · $minsLeft"
            }
            val big = buildString {
                appendLine(text)
                current.teacher?.let {
                    appendLine(context.getString(R.string.live_teacher_line, it))
                }
                next?.let {
                    if (it.kind == LiveLessonBoundary.Boundary.Kind.END) {
                        val clock = "%02d:%02d".format(it.at.hour, it.at.minute)
                        appendLine(context.getString(R.string.live_ends_at, clock))
                    }
                }
                val after = events
                    .filter { e -> e.start != null && e.start!!.isAfter(now) }
                    .minByOrNull { it.start!! }
                after?.let {
                    append(
                        context.getString(
                            R.string.live_next_line,
                            friendlyTitle(it),
                            it.timeLabel(context),
                        ),
                    )
                }
            }
            builder
                .setContentTitle(context.getString(R.string.live_lesson_in_progress))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big))
                .addAction(0, context.getString(R.string.live_open_schedule), open)
        } else if (next != null) {
            val mins = ChronoUnit.MINUTES.between(now, next.at).coerceAtLeast(0).toInt()
            val title = if (next.kind == LiveLessonBoundary.Boundary.Kind.START) {
                context.getString(R.string.live_next_in_minutes, mins)
            } else {
                context.getString(R.string.live_schedule_update_in, mins)
            }
            val clock = "%02d:%02d".format(next.at.hour, next.at.minute)
            val nextName = next.title.let { raw ->
                // Boundary titles are usually subject strings.
                settings.displayNameForSubject(raw, fallback = raw)
            }
            builder
                .setContentTitle(title)
                .setContentText(nextName)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        context.getString(
                            R.string.live_boundary_big,
                            nextName,
                            next.kind.name,
                            clock,
                        ),
                    ),
                )
        }

        nm.notify(notifId, builder.build())
    }

    fun clear() = nm.cancel(notifId)

    private fun friendlyTitle(event: ScheduleEvent): String {
        val key = event.team.ifBlank { event.title }
        return settings.displayNameForSubject(key, fallback = event.title.ifBlank { key })
    }

    companion object {
        private const val CHANNEL = "live_lesson"
    }
}
