package com.example.ebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow


@Composable
fun HistoryContent(
    chatDao: ChatDao,
    onSessionSelected: (String) -> Unit,
    onSessionDeleted: (String) -> Unit
) {
    var sessions by remember { mutableStateOf<List<ChatSessionEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Track which session's menu is open (by sessionId)
    var expandedSessionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        sessions = chatDao.getAllSessions()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
    ) {
        items(
            items = sessions,
            key = { it.sessionId }
        ) { session ->

            ListItem(
                headlineContent = {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = formatDate(session.createdAt),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    // 3-DOT OVERFLOW MENU
                    Box {
                        IconButton(
                            onClick = {
                                // Toggle menu for this session
                                expandedSessionId = if (expandedSessionId == session.sessionId)
                                    null
                                else
                                    session.sessionId
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Session options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // DROPDOWN MENU
                        DropdownMenu(
                            expanded = expandedSessionId == session.sessionId,
                            onDismissRequest = { expandedSessionId = null },
                            modifier = Modifier
                                .width(180.dp)
                                .shadow(4.dp)  // Subtle shadow for depth
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete session",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        // DELETE SESSION + ALL MESSAGES
                                        chatDao.deleteSessionWithMessages(session.sessionId)
                                        sessions = chatDao.getAllSessions()
                                        expandedSessionId = null  // Close menu after delete

                                        onSessionDeleted(session.sessionId)
                                    }
                                }
                            )

                            // Optional: Add more actions later
                            // DropdownMenuItem(
                            //     text = { Text("Rename") },
                            //     leadingIcon = { Icon(Icons.Default.Edit, null) },
                            //     onClick = { /* ... */ }
                            // )
                        }
                    }
                },
                modifier = Modifier.clickable {
                    onSessionSelected(session.sessionId)
                    expandedSessionId = null  // Close any open menu when selecting session
                }
            )

            Divider()
        }
    }
}

/* -------- DATE FORMATTER -------- */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat(
        "dd MMM yyyy, hh:mm a",
        Locale.getDefault()
    )
    return sdf.format(Date(timestamp))
}