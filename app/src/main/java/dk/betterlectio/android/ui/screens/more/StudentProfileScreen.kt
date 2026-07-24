package dk.betterlectio.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.EntryPointAccessors
import dk.betterlectio.android.R
import dk.betterlectio.android.core.util.LectioDateUtils
import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.InstagramHandles
import dk.betterlectio.android.feature.directory.StudentProfile
import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import dk.betterlectio.android.feature.schedule.ScheduleWeek
import dk.betterlectio.android.feature.schedule.statusLabelText
import dk.betterlectio.android.feature.schedule.timeLabelText
import dk.betterlectio.android.feature.settings.CalendarStyle
import dk.betterlectio.android.ui.components.AvatarRepositoryEntryPoint
import dk.betterlectio.android.ui.components.DateStrip
import dk.betterlectio.android.ui.components.DateStripDay
import dk.betterlectio.android.ui.components.DetailSheetHeader
import dk.betterlectio.android.ui.components.DetailSheetPadding
import dk.betterlectio.android.ui.components.InitialsAvatar
import dk.betterlectio.android.ui.components.LectioImagePreviewDialog
import dk.betterlectio.android.ui.components.LoadingBox
import dk.betterlectio.android.ui.components.StatusChip
import dk.betterlectio.android.ui.screens.schedule.StandardDayList
import dk.betterlectio.android.ui.screens.schedule.TimelineDayView
import dk.betterlectio.android.ui.theme.BetterLectioThemeExtras
import java.time.LocalDate

@Composable
fun StudentProfileScreen(
    loading: Boolean,
    entity: DirectoryEntity,
    profile: StudentProfile?,
    week: ScheduleWeek?,
    weekNumber: Int,
    weekYear: Int,
    pinned: Boolean,
    defaultCalendarStyle: CalendarStyle,
    displayTitle: (ScheduleEvent) -> String,
    accentFor: (ScheduleEvent) -> Color,
    onWriteMessage: () -> Unit,
    onTogglePin: () -> Unit,
    onViewClass: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToToday: () -> Unit,
    onLoadWeekForDate: (LocalDate) -> Unit,
) {
    if (loading && week == null) {
        LoadingBox()
        return
    }

    val hasBetterLectio = profile?.hasBetterLectio == true
    val displayName = profile?.displayName(entity.name) ?: entity.name
    val classLabel = profile?.className?.takeIf { it.isNotBlank() }
        ?: entity.subtitle?.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            StudentProfileHero(
                entity = entity,
                profile = profile,
                displayName = displayName,
                classLabel = classLabel,
                hasBetterLectio = hasBetterLectio,
                pinned = pinned,
                onWriteMessage = onWriteMessage,
                onTogglePin = onTogglePin,
                onViewClass = onViewClass,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            thickness = 0.5.dp,
        )
        PersonSchedulePane(
            loading = loading,
            week = week,
            weekNumber = weekNumber,
            weekYear = weekYear,
            defaultCalendarStyle = defaultCalendarStyle,
            displayTitle = displayTitle,
            accentFor = accentFor,
            onPrevWeek = onPrevWeek,
            onNextWeek = onNextWeek,
            onGoToToday = onGoToToday,
            onLoadWeekForDate = onLoadWeekForDate,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSchedulePane(
    loading: Boolean,
    week: ScheduleWeek?,
    weekNumber: Int,
    weekYear: Int,
    defaultCalendarStyle: CalendarStyle,
    displayTitle: (ScheduleEvent) -> String,
    accentFor: (ScheduleEvent) -> Color,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToToday: () -> Unit,
    onLoadWeekForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val today = LocalDate.now()
    val extended = BetterLectioThemeExtras.extendedColors
    var calendarStyle by remember(defaultCalendarStyle) { mutableStateOf(defaultCalendarStyle) }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedEvent by remember { mutableStateOf<ScheduleEvent?>(null) }

    LaunchedEffect(week?.year, week?.week, week?.days) {
        val days = week?.days.orEmpty()
        if (days.isEmpty()) return@LaunchedEffect
        val inWeek = days.any { it.date == selectedDate }
        if (!inWeek) {
            selectedDate = days.firstOrNull { it.date == today }?.date
                ?: days.firstOrNull { it.events.isNotEmpty() }?.date
                ?: days.first().date
        }
    }

    val weekDays = week?.days.orEmpty()
    val eventsForSelected = weekDays.find { it.date == selectedDate }?.events.orEmpty()
    val isCurrentWeek = weekYear == LectioDateUtils.isoWeekYear(today) &&
        weekNumber == LectioDateUtils.isoWeek(today)

    Column(modifier = modifier.fillMaxSize()) {
        PersonWeekHeader(
            weekNumber = week?.week ?: weekNumber,
            loading = loading,
            showToday = !isCurrentWeek || selectedDate != today,
            onPrevWeek = onPrevWeek,
            onNextWeek = onNextWeek,
            onGoToToday = {
                selectedDate = today
                onGoToToday()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = calendarStyle == CalendarStyle.PROFESSIONAL,
                onClick = { calendarStyle = CalendarStyle.PROFESSIONAL },
                label = { Text(stringResource(R.string.settings_calendar_timeline)) },
            )
            FilterChip(
                selected = calendarStyle == CalendarStyle.STANDARD,
                onClick = { calendarStyle = CalendarStyle.STANDARD },
                label = { Text(stringResource(R.string.settings_calendar_list)) },
            )
        }

        if (weekDays.isNotEmpty()) {
            DateStrip(
                days = weekDays.map { day ->
                    DateStripDay(
                        date = day.date,
                        hasEvents = day.events.isNotEmpty(),
                    )
                },
                selected = selectedDate,
                onSelect = { selectedDate = it },
                onWeekChanged = { date ->
                    selectedDate = date
                    onLoadWeekForDate(date)
                },
                hasEvents = { date ->
                    weekDays.find { it.date == date }?.events?.isNotEmpty() == true
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            thickness = 0.5.dp,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                loading && week == null -> LoadingBox()
                week == null -> {
                    Text(
                        stringResource(R.string.directory_person_schedule_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                calendarStyle == CalendarStyle.PROFESSIONAL -> {
                    TimelineDayView(
                        date = selectedDate,
                        events = eventsForSelected,
                        displayTitle = displayTitle,
                        accentFor = accentFor,
                        statusColor = { status ->
                            when (status) {
                                EventStatus.CHANGED -> extended.statusChanged
                                EventStatus.CANCELLED -> extended.statusCancelled
                                EventStatus.NORMAL -> extended.statusNormal
                            }
                        },
                        onEventClick = { selectedEvent = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    StandardDayList(
                        events = eventsForSelected,
                        displayTitle = displayTitle,
                        accentFor = accentFor,
                        onEventClick = { selectedEvent = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    selectedEvent?.let { event ->
        val accent = accentFor(event)
        val statusColor = when (event.status) {
            EventStatus.CHANGED -> extended.statusChanged
            EventStatus.CANCELLED -> extended.statusCancelled
            EventStatus.NORMAL -> extended.statusNormal
        }
        ModalBottomSheet(onDismissRequest = { selectedEvent = null }) {
            DetailSheetPadding {
                DetailSheetHeader(
                    title = displayTitle(event),
                    subtitle = event.timeLabelText(),
                    meta = listOfNotNull(event.teacher, event.room).joinToString(" · ")
                        .ifBlank { null },
                    trailing = {
                        event.statusLabelText()?.takeIf { it.isNotBlank() }?.let { label ->
                            StatusChip(text = label, color = statusColor)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                event.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                event.homework?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.homework_lesson_content),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentProfileHero(
    entity: DirectoryEntity,
    profile: StudentProfile?,
    displayName: String,
    classLabel: String?,
    hasBetterLectio: Boolean,
    pinned: Boolean,
    onWriteMessage: () -> Unit,
    onTogglePin: () -> Unit,
    onViewClass: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val avatarRepo = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AvatarRepositoryEntryPoint::class.java,
        ).avatarRepository()
    }
    val preferredUrl = profile?.pictureUrl(entity.avatarUrl)
    var resolvedUrl by remember(entity.id, preferredUrl) {
        mutableStateOf(
            preferredUrl
                ?: avatarRepo.peekUrl(
                    entityId = entity.id,
                    name = entity.name,
                    knownUrl = entity.avatarUrl,
                )
                ?: entity.avatarUrl,
        )
    }
    var showPhotoPreview by remember { mutableStateOf(false) }

    LaunchedEffect(entity.id, preferredUrl, entity.avatarUrl) {
        if (!preferredUrl.isNullOrBlank()) {
            resolvedUrl = preferredUrl
            return@LaunchedEffect
        }
        val resolved = avatarRepo.resolveUrl(
            entityId = entity.id,
            name = entity.name,
            kind = entity.kind,
            knownUrl = entity.avatarUrl ?: resolvedUrl,
        )
        if (!resolved.isNullOrBlank()) resolvedUrl = resolved
    }

    val pfpWidth = if (hasBetterLectio) 88.dp else 64.dp
    val pfpHeight = if (hasBetterLectio) 116.dp else 84.dp
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = pfpWidth, height = pfpHeight)
                        .clip(shape)
                        .border(
                            width = if (hasBetterLectio) 2.dp else 1.dp,
                            color = if (hasBetterLectio) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = shape,
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !resolvedUrl.isNullOrBlank()) {
                            showPhotoPreview = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val url = resolvedUrl
                    if (!url.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(
                                R.string.student_profile_photo_cd,
                                displayName,
                            ),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            loading = {
                                InitialsAvatar(
                                    label = displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            error = {
                                InitialsAvatar(
                                    label = displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )
                    } else {
                        InitialsAvatar(
                            label = displayName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        displayName,
                        style = if (hasBetterLectio) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        classLabel?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.directory_person_kind_student),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (hasBetterLectio) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.student_profile_bl_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onTogglePin,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                        ) {
                            Icon(
                                imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (pinned) R.string.directory_unpin else R.string.directory_pin,
                                ),
                                tint = if (pinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Button(
                            onClick = onWriteMessage,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.directory_write_message))
                        }
                    }
                }
            }

            if (hasBetterLectio) {
                profile?.description?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profile?.formattedBirthday()?.let { birthday ->
                        ProfileInfoChip(
                            icon = Icons.Default.Cake,
                            label = birthday,
                            contentDescription = stringResource(R.string.student_profile_birthday_cd),
                        )
                    }
                    val igHandle = InstagramHandles.format(profile?.instagram)
                    val igUrl = InstagramHandles.profileUrl(profile?.instagram)
                    if (igHandle.isNotEmpty() && igUrl != null) {
                        ProfileInfoChip(
                            icon = Icons.Default.Link,
                            label = igHandle,
                            contentDescription = stringResource(R.string.student_profile_instagram_cd),
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(igUrl)),
                                )
                            },
                        )
                    }
                    classLabel?.let { label ->
                        ProfileInfoChip(
                            icon = Icons.Default.School,
                            label = label,
                            contentDescription = null,
                        )
                    }
                }
                if (!classLabel.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onViewClass,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.directory_view_class_named, classLabel),
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.student_profile_no_betterlectio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!classLabel.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onViewClass,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.directory_view_class_named, classLabel),
                        )
                    }
                }
            }
        }
    }

    if (showPhotoPreview) {
        val url = resolvedUrl
        if (!url.isNullOrBlank()) {
            LectioImagePreviewDialog(
                url = url,
                contentDescription = displayName,
                onDismiss = { showPhotoPreview = false },
            )
        }
    }
}

@Composable
fun PersonWeekHeader(
    weekNumber: Int,
    loading: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
    showToday: Boolean = false,
    onGoToToday: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.directory_person_schedule) + " · uge $weekNumber",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showToday && onGoToToday != null) {
                TextButton(onClick = onGoToToday, enabled = !loading) {
                    Text(stringResource(R.string.schedule_go_to_today))
                }
            }
            IconButton(onClick = onPrevWeek, enabled = !loading) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.student_profile_week_prev_cd),
                )
            }
            IconButton(onClick = onNextWeek, enabled = !loading) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.student_profile_week_next_cd),
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoChip(
    icon: ImageVector,
    label: String,
    contentDescription: String?,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
