package com.famitube.app.ui.screens.notifications

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.data.model.NotificationItemDto
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.PinkPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    repository: FamiTubeRepository,
    onBack: () -> Unit,
    onUnreadCountChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<NotificationItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun loadNotifications() {
        scope.launch {
            isLoading = true
            errorMsg = null
            repository.getNotifications().onSuccess { data ->
                items = data.items
                onUnreadCountChanged(data.unreadCount)
                isLoading = false
            }.onFailure { err ->
                errorMsg = err.message ?: "Failed to load notifications"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNotifications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (items.any { !it.isRead }) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.markAllNotificationsRead().onSuccess {
                                    items = items.map { it.copy(isRead = true) }
                                    onUnreadCountChanged(0)
                                    Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark All Read")
                        }
                    }
                    if (items.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.clearNotifications().onSuccess {
                                    items = emptyList()
                                    onUnreadCountChanged(0)
                                    Toast.makeText(context, "Notification history cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PinkPrimary
                    )
                }
                errorMsg != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { loadNotifications() }) { Text("Retry") }
                    }
                }
                items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No notifications yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Requests, approvals, and safety alerts will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id }) { notif ->
                            val icon = when (notif.type) {
                                "VIDEO_REQUEST" -> Icons.Default.VideoLibrary
                                "VIDEO_DECISION" -> Icons.Default.CheckCircle
                                "SITE_REQUEST", "SITE_DECISION" -> Icons.Default.Public
                                "BLOCKED_SITE" -> Icons.Default.Block
                                else -> Icons.Default.Notifications
                            }
                            val tint = when (notif.type) {
                                "BLOCKED_SITE" -> MaterialTheme.colorScheme.error
                                "VIDEO_DECISION", "SITE_DECISION" -> Color(0xFF059669)
                                else -> PinkPrimary
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (!notif.isRead) {
                                            scope.launch {
                                                repository.markNotificationRead(notif.id).onSuccess {
                                                    items = items.map {
                                                        if (it.id == notif.id) it.copy(isRead = true) else it
                                                    }
                                                    onUnreadCountChanged(items.count { !it.isRead })
                                                }
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.isRead)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tint.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = tint)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = notif.title,
                                                fontWeight = if (notif.isRead) FontWeight.SemiBold else FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (!notif.isRead) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(PinkPrimary)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.message,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = notif.createdAt,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
