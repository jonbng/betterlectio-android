package dk.betterlectio.android.feature.schedule

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dk.betterlectio.android.R

fun ScheduleEvent.timeLabel(context: Context): String = when {
    isAllDay -> context.getString(R.string.event_all_day)
    start != null && end != null ->
        "%02d:%02d – %02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
    else -> ""
}

fun ScheduleEvent.statusLabel(context: Context): String? = when (status) {
    EventStatus.CHANGED -> context.getString(R.string.event_status_changed)
    EventStatus.CANCELLED -> context.getString(R.string.event_status_cancelled)
    EventStatus.NORMAL -> null
}

@Composable
@ReadOnlyComposable
fun ScheduleEvent.timeLabelText(): String {
    val context = LocalContext.current
    return timeLabel(context)
}

@Composable
@ReadOnlyComposable
fun ScheduleEvent.statusLabelText(): String? {
    val context = LocalContext.current
    return statusLabel(context)
}

/** Protocol / identity token for local private events (not for display). */
const val PRIVATE_EVENT_TEAM_TOKEN = "Privat"

/** Stored default title when draft title is blank (matched in canDelete checks). */
const val PRIVATE_EVENT_DEFAULT_TITLE = "Privat aftale"
