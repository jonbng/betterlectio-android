package dk.betterlectio.android.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.betterlectio.android.feature.feedback.FeedbackInboxItem
import dk.betterlectio.android.feature.feedback.FeedbackThread
import kotlinx.coroutines.launch

@Composable
fun FeedbackInboxPane(
    onClose: () -> Unit,
    onCompose: () -> Unit,
    onList: suspend () -> List<FeedbackInboxItem>,
    onThread: suspend (String) -> FeedbackThread,
    onReply: suspend (String, String) -> Unit,
) {
    var list by remember { mutableStateOf<List<FeedbackInboxItem>>(emptyList()) }
    var thread by remember { mutableStateOf<FeedbackThread?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadList() {
        loading = true
        runCatching { onList() }
            .onSuccess { list = it; error = null }
            .onFailure { error = it.localizedMessage ?: "Kunne ikke hente feedback" }
        loading = false
    }
    suspend fun open(item: FeedbackInboxItem) {
        loading = true
        runCatching { onThread(item.id) }
            .onSuccess { thread = it; error = null }
            .onFailure { error = it.localizedMessage ?: "Kunne ikke hente beskeden" }
        loading = false
    }

    LaunchedEffect(Unit) { loadList() }

    Column(
        Modifier
            .fillMaxWidth()
            .height(620.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thread != null) {
                IconButton(onClick = { thread = null; scope.launch { loadList() } }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Tilbage")
                }
            }
            Text(
                thread?.item?.displayTitle ?: "Min feedback",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (thread == null) TextButton(onClick = onCompose) { Text("Ny besked") }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Luk") }
        }
        HorizontalDivider()

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            thread != null -> {
                val current = thread!!
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${conversationLabel(current.item.conversationState)} · ${statusLabel(current.item.status)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(current.item.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (current.statusEvents.size > 1) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Forløb", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    current.statusEvents.forEach { event -> Text(statusLabel(event.toStatus), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
                                }
                            }
                        }
                    }
                    items(current.comments, key = { it.id }) { comment ->
                        Row(Modifier.fillMaxWidth()) {
                            if (comment.authorKind == "user") Spacer(Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.86f),
                                shape = RoundedCornerShape(16.dp),
                                color = if (comment.authorKind == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (comment.authorKind == "user") "Dig" else "BetterLectio", style = MaterialTheme.typography.labelSmall, color = if (comment.authorKind == "user") MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = if (comment.authorKind == "user") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (comment.authorKind != "user") Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = reply, onValueChange = { if (it.length <= 4_000) reply = it }, modifier = Modifier.weight(1f), placeholder = { Text("Skriv et svar…") }, maxLines = 4, shape = RoundedCornerShape(16.dp))
                    Button(
                        onClick = { scope.launch { sending = true; runCatching { onReply(current.item.id, reply); onThread(current.item.id) }.onSuccess { thread = it; reply = ""; error = null }.onFailure { error = it.localizedMessage }; sending = false } },
                        enabled = reply.isNotBlank() && !sending,
                        modifier = Modifier.height(56.dp),
                    ) { if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send") }
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Inbox, contentDescription = null, Modifier.size(36.dp)); Spacer(Modifier.height(10.dp)); Text("Ingen beskeder endnu"); TextButton(onClick = onCompose) { Text("Send feedback") } }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { open(item) } }.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(9.dp).background(if (item.isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Column(Modifier.weight(1f)) { Text(item.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(conversationLabel(item.conversationState), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    HorizontalDivider(Modifier.padding(start = 39.dp))
                }
            }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun conversationLabel(value: String) = when (value) {
    "awaiting_staff" -> "Venter på BetterLectio"
    "awaiting_user" -> "Venter på dig"
    "resolved" -> "Løst"
    else -> value
}

private fun statusLabel(value: String) = when (value) {
    "pending" -> "Modtaget"
    "review" -> "Vi undersøger det"
    "planned" -> "Planlagt"
    "in_progress" -> "I gang"
    "completed" -> "Udgivet"
    "declined" -> "Ikke planlagt"
    "duplicate" -> "Knyttet til anden sag"
    else -> value
}
