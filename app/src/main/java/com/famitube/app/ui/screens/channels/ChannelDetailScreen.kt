package com.famitube.app.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.famitube.app.data.model.ChannelAccessData
import com.famitube.app.data.model.ChannelDetailResponse
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.ChannelQuizWall
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
    onVideoClick: (String) -> Unit
) {
    var detail by remember { mutableStateOf<ChannelDetailResponse?>(null) }
    var lockedAccess by remember { mutableStateOf<ChannelAccessData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()
    val scope = rememberCoroutineScope()

    fun loadChannel() {
        scope.launch {
            isLoading = true
            val accessRes = repository.getChannelAccess(channelId)
            val access = accessRes.getOrNull()
            if (access != null && !access.unlocked) {
                lockedAccess = access
                isLoading = false
                return@launch
            }
            lockedAccess = null
            val res = repository.getChannelDetail(channelId)
            detail = res.getOrNull()
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
            } else if (lockedAccess != null) {
                ChannelQuizWall(
                    repository = repository,
                    channelId = channelId,
                    initialAccess = lockedAccess!!,
                    onUnlocked = { loadChannel() }
                )
            } else if (detail != null) {
                val channel = detail!!.channel
                val videos = detail!!.videos

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
                        }
                    }

                    item {
                        Text(
                            text = "Channel Videos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(videos) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
