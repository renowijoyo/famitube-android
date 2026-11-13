package com.kidstube.app.ui.screens.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kidstube.app.data.model.LiveChannelState
import com.kidstube.app.data.model.SourceType
import com.kidstube.app.data.repository.KidsTubeRepository
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark
import com.kidstube.app.ui.theme.SurfaceLightDark
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvPlayerScreen(
    repository: KidsTubeRepository,
    channelId: String,
    onBackClick: () -> Unit
) {
    var liveState by remember { mutableStateOf<LiveChannelState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var elapsedInVideo by remember { mutableIntStateOf(0) }
    var scheduleVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun fetchSchedule() {
        scope.launch {
            val res = repository.getLiveChannelState(channelId)
            val data = res.getOrNull()
            if (data != null) {
                liveState = data
                elapsedInVideo = data.offsetSeconds
                remainingSeconds = data.remainingSeconds
                scheduleVersion++
            }
            isLoading = false
        }
    }

    LaunchedEffect(channelId) {
        fetchSchedule()
    }

    // Wall-clock synced countdown: auto-advances to next show when current video ends
    LaunchedEffect(scheduleVersion) {
        val state = liveState ?: return@LaunchedEffect
        if (state.currentVideo == null) return@LaunchedEffect

        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
            elapsedInVideo++
            if (elapsedInVideo % 5 == 0) {
                repository.getPreferences().addUsedWatchTime(5)
            }
        }
        // Current video ended on schedule -> fetch next airing video
        fetchSchedule()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PinkPrimary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE TV",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = liveState?.channel?.name ?: "Live TV",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
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
                .background(Color(0xFF0B1120))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (liveState?.currentVideo == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No programs scheduled on this channel yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add videos to this Live TV channel in the KidsTube Studio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val video = liveState!!.currentVideo!!
                val totalDur = video.duration.coerceAtLeast(1)
                val progress = (elapsedInVideo.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 1. Locked-Runtime Video Player (User cannot pause, seek, or scrub)
                    item(key = "${video.id}_$scheduleVersion") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            if (video.sourceType == SourceType.YOUTUBE && !video.youtubeVideoId.isNullOrBlank()) {
                                LockedYouTubePlayerComposable(
                                    videoId = video.youtubeVideoId,
                                    startSeconds = liveState!!.offsetSeconds.toFloat(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LockedExoPlayerComposable(
                                    mediaUrl = video.sourceUrl,
                                    startPositionMs = liveState!!.offsetSeconds * 1000L,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Non-interactive transparent shield so user cannot tap/seek/pause runtime
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { /* Intentionally blocked: Live TV runtime is locked */ }
                                    )
                            )

                            // Live Broadcast Pill
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PinkPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE BROADCAST",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 2. Read-only Broadcast Progress & Info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark)
                                .padding(16.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = PinkPrimary,
                                trackColor = SurfaceLightDark
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NOW PLAYING",
                                    color = PinkPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Runtime Locked",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${remainingSeconds / 60}m ${remainingSeconds % 60}s left",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            if (video.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = video.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. TV Guide: Up Next Schedule
                    if (liveState!!.upNext.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF4CC9F0),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Up Next on ${liveState!!.channel.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(liveState!!.upNext) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.video.thumbnailUrl,
                                        contentDescription = item.video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.video.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val mins = item.startsInSeconds / 60
                                        val secs = item.startsInSeconds % 60
                                        Text(
                                            text = if (mins > 0) "Starts in ${mins}m ${secs}s" else "Starts in ${secs}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CC9F0)
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

@Composable
fun LockedYouTubePlayerComposable(
    videoId: String,
    startSeconds: Float,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            YouTubePlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                enableAutomaticInitialization = false
                val options = IFramePlayerOptions.Builder()
                    .controls(0)
                    .rel(0)
                    .build()
                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoId, startSeconds)
                    }
                }, options)
            }
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
fun LockedExoPlayerComposable(
    mediaUrl: String,
    startPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(mediaUrl, startPositionMs) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl))
            setMediaItem(mediaItem)
            seekTo(startPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(mediaUrl, startPositionMs) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Runtime controls disabled for Live TV
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
