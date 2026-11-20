package com.famitube.app.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.data.model.ChannelDetailResponse
import com.famitube.app.data.model.ChannelType
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    repository: FamiTubeRepository,
    channelId: String,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onWatchLive: ((String) -> Unit)? = null
) {
    var detail by remember { mutableStateOf<ChannelDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isSubscribing by remember { mutableStateOf(false) }
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()
    val scope = rememberCoroutineScope()

    fun loadChannel() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val res = repository.getChannelDetail(channelId)
            val d = res.getOrNull()
            if (d != null) {
                detail = d
            } else {
                // Fallback: search repository channels & cached feed videos
                val channelsRes = repository.getChannels()
                val foundChannel = channelsRes.getOrNull()?.find { it.id == channelId }
                if (foundChannel != null) {
                    val videosRes = repository.getVideos()
                    val channelVideos = videosRes.getOrNull()?.filter {
                        it.channelId == channelId || it.channelTitle.equals(foundChannel.name, ignoreCase = true)
                    } ?: emptyList()
                    detail = ChannelDetailResponse(channel = foundChannel, videos = channelVideos, liveState = null)
                } else {
                    errorMessage = res.exceptionOrNull()?.message ?: "Unable to load channel"
                }
            }

            // Check subscription state
            val savedRes = repository.getKidSavedChannels()
            val saved = savedRes.getOrNull() ?: emptyList()
            isSubscribed = saved.any { it.id == channelId }

            isLoading = false
        }
    }

    LaunchedEffect(channelId, ageFilter) {
        loadChannel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.channel?.name ?: "Channel", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (detail != null) {
                val channel = detail!!.channel
                val videos = detail!!.videos
                val isLiveTv = channel.channelType == ChannelType.LIVE_TV

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Channel Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = channel.avatarUrl,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (channel.isVerified) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = Color(0xFF4CC9F0),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    val (ageLabel, _) = formatChannelAgeBadge(channel.ageRating)
                                    Text(
                                        text = "${channel.category} • $ageLabel • ${videos.size} videos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PinkPrimary
                                    )
                                    Text(
                                        text = "Curated by ${channel.ownerName ?: "Team FamiTube"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (channel.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = channel.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (isSubscribing) return@Button
                                        scope.launch {
                                            isSubscribing = true
                                            if (isSubscribed) {
                                                val res = repository.removeKidSavedChannel(channel.id)
                                                if (res.isSuccess) isSubscribed = false
                                            } else {
                                                val res = repository.addKidSavedChannel(channel.id)
                                                if (res.isSuccess) isSubscribed = true
                                            }
                                            isSubscribing = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else PinkPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isSubscribed) "Subscribed ✓" else "+ Subscribe",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSubscribed) MaterialTheme.colorScheme.onSurface else Color.White
                                    )
                                }

                                if (isLiveTv && onWatchLive != null) {
                                    Button(
                                        onClick = { onWatchLive(channel.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Watch Live", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Channel Videos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${videos.size} videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (videos.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🎬", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No videos found in this channel",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "There are no videos matching the active age tier.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(videos) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            } else {
                // Error / Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMessage ?: "Channel could not be found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadChannel() },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private fun formatChannelAgeBadge(ageRating: String): Pair<String, Color> {
    return when (ageRating.uppercase()) {
        "PRESCHOOL" -> "Preschool (0-4)" to Color(0xFF06D6A0)
        "YOUNG_KIDS" -> "Young Kids (5-7)" to Color(0xFF4CC9F0)
        "OLDER_KIDS" -> "Older Kids (8-12)" to Color(0xFFFFB703)
        "TEENS" -> "Teens (13+)" to Color(0xFFFF758C)
        else -> "All Ages" to Color(0xFF4CC9F0)
    }
}
