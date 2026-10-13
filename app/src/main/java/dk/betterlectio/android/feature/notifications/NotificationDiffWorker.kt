package dk.betterlectio.android.feature.notifications

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dk.betterlectio.android.BuildConfig
import dk.betterlectio.android.MainActivity
import dk.betterlectio.android.R
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.assignments.AssignmentRepository
import dk.betterlectio.android.feature.live.LiveLessonNotifier
import dk.betterlectio.android.feature.live.LiveLessonScheduler
import dk.betterlectio.android.feature.messages.MessageFolder
import dk.betterlectio.android.feature.messages.MessageRepository
import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.ScheduleRepository
import dk.betterlectio.android.feature.schedule.statusLabel
import dk.betterlectio.android.feature.schedule.timeLabel
import dk.betterlectio.android.feature.settings.SettingsStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.Locale

class NotificationDiffWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun scheduleRepository(): ScheduleRepository
        fun messageRepository(): MessageRepository
        fun assignmentRepository(): AssignmentRepository
        fun settingsStore(): SettingsStore
        fun sessionController(): SessionController
        fun liveLessonNotifier(): LiveLessonNotifier
        fun liveLessonScheduler(): LiveLessonScheduler
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.ADMIN_BUILD) return Result.success()
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            Deps::class.java,
        )
        val session = deps.sessionController()
        val student = session.currentStudent
        if (student == null || student.isDemo) return Result.success()

        ensureChannel()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val canPostNotifications = NotificationManagerCompat.from(applicationContext)
            .areNotificationsEnabled()
        val settings = deps.settingsStore()
        val prefs = applicationContext.getSharedPreferences("notif_snapshots", Context.MODE_PRIVATE)
        SnapshotCategory.entries.forEach { category ->
            val enabled = when (category) {
                SnapshotCategory.EVENTS -> settings.notifEvents.value
                SnapshotCategory.MESSAGES -> settings.notifMessages.value
                SnapshotCategory.ASSIGNMENTS -> settings.notifAssignments.value
            }
            if (!enabled) prefs.edit { putBoolean(categoryEnabledKey(student.studentId, category), false) }
        }
        val ctx = applicationContext
        val scheduleResult = deps.scheduleRepository().loadWeek(forceRefresh = true)
        if (scheduleResult is AppResult.Success) {
            val todayEvents = scheduleResult.data.days
                .find { it.date == LocalDate.now() }
                ?.events
                .orEmpty()
            val now = LocalDateTime.now()
            deps.liveLessonNotifier().update(todayEvents, now)
            deps.liveLessonScheduler().scheduleBoundaries(todayEvents, now)
        }

        when (val week = scheduleResult) {
            is AppResult.Success -> {
                val allEvents = week.data.days.flatMap { it.events }
                val changed = allEvents.filter { it.status != EventStatus.NORMAL }
                val snapshotKey = snapshotKey(student.studentId, SnapshotCategory.EVENTS)
                val keys = allEvents.map {
                    NotificationSnapshotDiff.eventKey(it.id, it.status.name)
                }.toSet()
                val fresh = NotificationSnapshotDiff.freshIds(
                    previous = prefs.getStringSet(snapshotKey, emptySet())?.toSet().orEmpty(),
                    current = keys,
                    initialized = prefs.contains(snapshotKey) &&
                        prefs.getBoolean(categoryEnabledKey(student.studentId, SnapshotCategory.EVENTS), true),
                )
                if (settings.notifEvents.value && canPostNotifications) {
                    changed.filter { NotificationSnapshotDiff.eventKey(it.id, it.status.name) in fresh }
                        .take(5)
                        .forEach { ev ->
                            val status = ev.statusLabel(ctx)?.lowercase(Locale.getDefault())
                                ?: ctx.getString(R.string.notif_module_changed_fallback)
                            val title = ctx.getString(R.string.notif_module_status, status)
                            val body = ctx.getString(
                                R.string.notif_event_line,
                                ev.title,
                                ev.timeLabel(ctx),
                            )
                            val notificationIdentity = "event:${ev.id}"
                            nm.notify(
                                NotificationSnapshotDiff.notificationId(notificationIdentity),
                                NotificationCompat.Builder(ctx, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_notification)
                                    .setColor(ContextCompat.getColor(ctx, R.color.brand_blue))
                                    .setContentTitle(title)
                                    .setContentText(body)
                                    .setContentIntent(
                                        openAppIntent(notificationIdentity, DESTINATION_SCHEDULE),
                                    )
                                    .setAutoCancel(true)
                                    .build(),
                            )
                            settings.appendNotificationHistory(
                                ctx.getString(R.string.notif_history_module, title, ev.title),
                            )
                        }
                }
                prefs.edit {
                    putStringSet(snapshotKey, keys)
                    putBoolean(
                        categoryEnabledKey(student.studentId, SnapshotCategory.EVENTS),
                        settings.notifEvents.value,
                    )
                }
            }
            else -> Unit
        }

        if (settings.notifMessages.value) {
            when (val folder = deps.messageRepository().loadFolder(MessageFolder.UNREAD, forceRefresh = true)) {
                is AppResult.Success -> {
                    val snapshotKey = snapshotKey(student.studentId, SnapshotCategory.MESSAGES)
                    val keys = folder.data.map { NotificationSnapshotDiff.messageKey(it.id) }.toSet()
                    val fresh = NotificationSnapshotDiff.freshIds(
                        previous = prefs.getStringSet(snapshotKey, emptySet())?.toSet().orEmpty(),
                        current = keys,
                        initialized = prefs.contains(snapshotKey) &&
                            prefs.getBoolean(
                                categoryEnabledKey(student.studentId, SnapshotCategory.MESSAGES),
                                true,
                            ),
                    )
                    if (fresh.isNotEmpty() && canPostNotifications) {
                        // The notification poll refreshes Ulæst, but the Messages screen opens
                        // Nyeste. Warm that folder too so tapping into the app cannot show the
                        // still-fresh five-minute cache without the message we just announced.
                        deps.messageRepository().loadFolder(
                            MessageFolder.NEWEST,
                            forceRefresh = true,
                        )
                        val notificationKey = "messages:new"
                        nm.notify(
                            NotificationSnapshotDiff.notificationId(notificationKey),
                            NotificationCompat.Builder(ctx, CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setColor(ContextCompat.getColor(ctx, R.color.brand_blue))
                                .setContentTitle(ctx.getString(R.string.notif_new_messages_title))
                                .setContentText(
                                    ctx.getString(R.string.notif_new_messages_body, fresh.size),
                                )
                                .setContentIntent(openAppIntent(notificationKey, DESTINATION_MESSAGES))
                                .setAutoCancel(true)
                                .build(),
                        )
                        settings.appendNotificationHistory(
                            ctx.getString(R.string.notif_history_new_messages, fresh.size),
                        )
                    }
                    prefs.edit {
                        putStringSet(snapshotKey, keys)
                        putBoolean(categoryEnabledKey(student.studentId, SnapshotCategory.MESSAGES), true)
                    }
                }
                else -> Unit
            }
        }

        if (settings.notifAssignments.value) {
            when (val a = deps.assignmentRepository().load(forceRefresh = true)) {
                is AppResult.Success -> {
                    val snapshotKey = snapshotKey(student.studentId, SnapshotCategory.ASSIGNMENTS)
                    val keys = a.data.map {
                        NotificationSnapshotDiff.assignmentKey(it.id, it.status)
                    }.toSet()
                    val fresh = NotificationSnapshotDiff.freshIds(
                        previous = prefs.getStringSet(snapshotKey, emptySet())?.toSet().orEmpty(),
                        current = keys,
                        initialized = prefs.contains(snapshotKey) &&
                            prefs.getBoolean(
                                categoryEnabledKey(student.studentId, SnapshotCategory.ASSIGNMENTS),
                                true,
                            ),
                    )
                    if (canPostNotifications) a.data.filter {
                        NotificationSnapshotDiff.assignmentKey(it.id, it.status) in fresh
                    }.take(3).forEach { item ->
                        val notificationIdentity = "assignment:${item.id}"
                        nm.notify(
                            NotificationSnapshotDiff.notificationId(notificationIdentity),
                            NotificationCompat.Builder(ctx, CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setColor(ContextCompat.getColor(ctx, R.color.brand_blue))
                                .setContentTitle(ctx.getString(R.string.notif_assignment_title, item.title))
                                .setContentText(
                                    ctx.getString(R.string.notif_assignment_body, item.team, item.status),
                                )
                                .setContentIntent(
                                    openAppIntent(notificationIdentity, DESTINATION_ASSIGNMENTS),
                                )
                                .setAutoCancel(true)
                                .build(),
                        )
                        settings.appendNotificationHistory(
                            ctx.getString(R.string.notif_history_assignment, item.status, item.title),
                        )
                    }
                    prefs.edit {
                        putStringSet(snapshotKey, keys)
                        putBoolean(categoryEnabledKey(student.studentId, SnapshotCategory.ASSIGNMENTS), true)
                    }
                }
                else -> Unit
            }
        }

        return Result.success()
    }

    private fun openAppIntent(notificationKey: String, destination: String): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        NotificationSnapshotDiff.notificationId(notificationKey),
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, destination)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "betterlectio"
        const val EXTRA_DESTINATION = "dk.betterlectio.android.notification.DESTINATION"
        const val DESTINATION_SCHEDULE = "schedule"
        const val DESTINATION_MESSAGES = "messages"
        const val DESTINATION_ASSIGNMENTS = "assignments"
        private const val WORK_NAME = "bl_notif_poll"

        private enum class SnapshotCategory(val key: String) {
            EVENTS("events"),
            MESSAGES("messages"),
            ASSIGNMENTS("assignments"),
        }

        private fun snapshotKey(studentId: String, category: SnapshotCategory): String =
            "snapshot_v2_${category.key}_$studentId"

        private fun categoryEnabledKey(studentId: String, category: SnapshotCategory): String =
            "snapshot_v2_${category.key}_enabled_$studentId"

        fun enqueue(context: Context) {
            if (BuildConfig.ADMIN_BUILD) return
            val req = PeriodicWorkRequestBuilder<NotificationDiffWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
