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

@Composable
fun HistoryScreen(
    chatDao: ChatDao,
    onSessionSelected: (String) -> Unit
) {
    var sessions by remember {
        mutableStateOf<List<ChatSessionEntity>>(emptyList())
    }

    val scope = rememberCoroutineScope()

    /* -------- LOAD CHAT HISTORY -------- */
    LaunchedEffect(Unit) {
        sessions = chatDao.getAllSessions()
    }

    /* -------- UI -------- */
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding() //  Only change: prevents content from going under system bars
    ) {
        items(
            items = sessions,
            key = { it.sessionId }     //  important for stable UI updates
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
                    IconButton(
                        onClick = {
                            scope.launch {
                                //  DELETE SESSION + ALL MESSAGES
                                chatDao.deleteSessionWithMessages(session.sessionId)
                                sessions = chatDao.getAllSessions()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete session"
                        )
                    }
                },
                modifier = Modifier.clickable {
                    onSessionSelected(session.sessionId)
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