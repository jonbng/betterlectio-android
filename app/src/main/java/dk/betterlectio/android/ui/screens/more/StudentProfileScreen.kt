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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.InstagramHandles
import dk.betterlectio.android.feature.directory.StudentProfile
import dk.betterlectio.android.feature.schedule.ScheduleWeek
import dk.betterlectio.android.feature.schedule.timeLabelText
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListMeta
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.AvatarRepositoryEntryPoint
import dk.betterlectio.android.ui.components.InitialsAvatar
import dk.betterlectio.android.ui.components.LoadingBox
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StudentProfileScreen(
    loading: Boolean,
    entity: DirectoryEntity,
    profile: StudentProfile?,
    week: ScheduleWeek?,
    weekNumber: Int,
    pinned: Boolean,
    listState: LazyListState,
    onWriteMessage: () -> Unit,
    onTogglePin: () -> Unit,
    onViewClass: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    if (loading && week == null) {
        LoadingBox()
        return
    }

    val hasBetterLectio = profile?.hasBetterLectio == true
    val displayName = profile?.displayName(entity.name) ?: entity.name
    val classLabel = profile?.className?.takeIf { it.isNotBlank() }
        ?: entity.subtitle?.takeIf { it.isNotBlank() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "hero") {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item(key = "week-header") {
            PersonWeekHeader(
                weekNumber = week?.week ?: weekNumber,
                loading = loading,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (loading && week == null) {
            item(key = "week-loading") {
                LoadingBox(Modifier.padding(24.dp))
            }
        } else if (week == null || week.days.all { it.events.isEmpty() }) {
            item(key = "week-empty") {
                Text(
                    stringResource(R.string.directory_person_schedule_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            week.days.forEach { day ->
                if (day.events.isEmpty()) return@forEach
                item(key = "pday-${day.date}") {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            " ${day.date.dayOfMonth}/${day.date.monthValue}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(day.events, key = { "pe-${it.id}" }) { ev ->
                    AppListRow {
                        AppListPrimary(ev.title, emphasized = true)
                        AppListSecondary(ev.timeLabelText())
                        ev.teacher?.let { AppListMeta(it) }
                        ev.room?.let { AppListMeta(it) }
                    }
                    AppListDivider()
                }
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

    val pfpWidth = if (hasBetterLectio) 90.dp else 60.dp
    val pfpHeight = if (hasBetterLectio) 120.dp else 80.dp
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = pfpWidth, height = pfpHeight)
                    .clip(shape)
                    .border(
                        width = if (hasBetterLectio) 2.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val url = resolvedUrl
                if (!url.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = displayName,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onTogglePin) {
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
                        )
                    }
                    Button(onClick = onWriteMessage) {
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
                        icon = Icons.Default.PhotoCamera,
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
                OutlinedButton(onClick = onViewClass) {
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
                OutlinedButton(onClick = onViewClass) {
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

@Composable
fun PersonWeekHeader(
    weekNumber: Int,
    loading: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
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
        Row {
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
