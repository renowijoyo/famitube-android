package com.famitube.app.ui.screens.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.famitube.app.data.model.KidPlaylist
import com.famitube.app.data.model.SourceType
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import com.famitube.app.ui.theme.SurfaceLightDark
import com.famitube.app.ui.components.YouTubeIFramePlayer

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onRelatedVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIdsFlow.collectAsState()
    val ageFilter by viewModel.ageFilterFlow.collectAsState()
    val isToddler = ageFilter == "TODDLER"
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
    var isFullscreen by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var kidPlaylists by remember { mutableStateOf<List<KidPlaylist>>(emptyList()) }
    var showCreateInPlayer by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showToddlerRestartDialog by remember { mutableStateOf(false) }
    var playerRestartKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(isToddler) {
        if (isToddler) {
            showAddToPlaylistDialog = false
            showCreateInPlayer = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCameraPermission(granted)
    }

    LaunchedEffect(state.isAttentionTrackingEnabled) {
        if (state.isAttentionTrackingEnabled) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.updateCameraPermission(granted)
            if (!granted) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    DisposableEffect(state.isAttentionTrackingEnabled, state.hasCameraPermission, state.isLimitExceeded) {
        val tracker = if (state.isAttentionTrackingEnabled && state.hasCameraPermission && !state.isLimitExceeded) {
            FrontCameraAttentionTracker(context) { isViewing ->
                viewModel.onAttentionSample(isViewing)
            }.also { it.start() }
        } else {
            null
        }
        onDispose {
            tracker?.stop()
        }
    }

    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(state.video?.title ?: "FamiTube Player", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (state.video != null && !state.isLimitExceeded) {
                            if (isToddler) {
                                IconButton(onClick = { showToddlerRestartDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Restart from Beginning",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = { isFullscreen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B1120))
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PinkPrimary
                    )
                }
                state.isLimitExceeded -> {
                    ScreenTimeLimitExceededView(onBackClick = onBackClick)
                }
                state.video != null -> {
                    val video = state.video!!

                    if (isFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            key(playerRestartKey) {
                                if (video.sourceType == SourceType.YOUTUBE && !video.youtubeVideoId.isNullOrBlank()) {
                                    YouTubePlayerComposable(
                                        videoId = video.youtubeVideoId,
                                        isPaused = state.isAutoPausedByInattention,
                                        controls = !isToddler,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ExoPlayerComposable(
                                        mediaUrl = video.sourceUrl,
                                        isPaused = state.isAutoPausedByInattention,
                                        useController = !isToddler,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            // Toddler scrub-lock shield: prevents toddler from skipping ahead
                            if (isToddler) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {}
                                        )
                                )
                            }
                            if (state.isAutoPausedByInattention) {
                                InattentionPausedOverlay(
                                    awaySeconds = state.attentionPauseSeconds,
                                    onResumeClick = { viewModel.resumeFromInattention() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isToddler) {
                                    IconButton(
                                        onClick = { showToddlerRestartDialog = true },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0x88000000))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Restart Video",
                                            tint = Color.White
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { isFullscreen = false },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x88000000))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // 1. Video Player Container
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color.Black)
                            ) {
                                key(playerRestartKey) {
                                    if (video.sourceType == SourceType.YOUTUBE && !video.youtubeVideoId.isNullOrBlank()) {
                                        YouTubePlayerComposable(
                                            videoId = video.youtubeVideoId,
                                            isPaused = state.isAutoPausedByInattention,
                                            controls = !isToddler,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        ExoPlayerComposable(
                                            mediaUrl = video.sourceUrl,
                                            isPaused = state.isAutoPausedByInattention,
                                            useController = !isToddler,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                // Toddler scrub-lock shield: prevents toddler from skipping ahead
                                if (isToddler) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {}
                                            )
                                    )
                                }
                                if (state.isAutoPausedByInattention) {
                                    InattentionPausedOverlay(
                                        awaySeconds = state.attentionPauseSeconds,
                                        onResumeClick = { viewModel.resumeFromInattention() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // 2. Video Title & Quick Actions
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceDark)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                if (state.isAttentionTrackingEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val statusColor = when {
                                        !state.hasCameraPermission -> Color(0xFF94A3B8)
                                        state.isAutoPausedByInattention -> Color(0xFFFF5252)
                                        state.isViewerPresent -> Color(0xFF06D6A0)
                                        else -> Color(0xFFFFB703)
                                    }
                                    val statusLabel = when {
                                        !state.hasCameraPermission -> "📷 Camera permission needed for attention check"
                                        state.isAutoPausedByInattention -> "⏸️ Auto-stopped (No viewer detected)"
                                        state.isViewerPresent -> "👀 Actively Viewing"
                                        else -> "😶 Not Viewing (Playing Unwatched)"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(statusColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                        if (state.hasCameraPermission) {
                                            val rewardSuffix = if (state.hasEarnedCompletionReward) " ✓" else ""
                                            Text(
                                                text = "Viewed: ${state.viewedSeconds}/${state.requiredViewedSeconds}s$rewardSuffix",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val canClickChannel = onChannelClick != null && video.channelId.isNotBlank()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = canClickChannel) {
                                                onChannelClick?.invoke(video.channelId)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = video.channelAvatar,
                                            contentDescription = video.channelTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video.channelTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${video.views} views • ${video.ageRating.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (!isToddler) {
                                        // Save to Library List Button
                                        IconButton(
                                            onClick = {
                                                viewModel.getKidPlaylists { lists ->
                                                    kidPlaylists = lists
                                                    showAddToPlaylistDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(SurfaceLightDark)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistAdd,
                                                contentDescription = "Save to Library List",
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    // Favorite Button
                                    IconButton(
                                        onClick = { viewModel.toggleFavorite() },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceLightDark)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (state.isFavorite) PinkPrimary else Color.White
                                        )
                                    }
                                }

                                if (isToddler) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { showToddlerRestartDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CC9F0))
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Restart Video from Beginning", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (video.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceLightDark)
                                    ) {
                                        Text(
                                            text = video.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Related Curated Videos
                        if (video.relatedVideos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "More to Watch",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp)
                                )
                            }

                            items(video.relatedVideos) { related ->
                                VideoCard(
                                    video = related,
                                    onClick = { onRelatedVideoClick(related.id) },
                                    isFavorite = related.id in favoriteIds,
                                    onChannelClick = onChannelClick,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    // Add to Custom Library List Dialog
    if (showAddToPlaylistDialog && !isToddler) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = {
                Text("Save to Library List", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            newPlaylistTitle = ""
                            showCreateInPlayer = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New List", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (kidPlaylists.isEmpty()) {
                        Text(
                            text = "No custom lists yet. Create your first list above!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        )
                    } else {
                        Text(
                            text = "Choose a list to add to:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            kidPlaylists.forEach { pl ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addVideoToPlaylist(pl.id) { success ->
                                                showAddToPlaylistDialog = false
                                                toastMessage = if (success) "Saved to ${pl.title}!" else "Failed to save"
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(pl.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            Text("${pl.items.size} videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = PinkPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Create New List from Player Dialog
    if (showCreateInPlayer && !isToddler) {
        AlertDialog(
            onDismissRequest = { showCreateInPlayer = false },
            title = { Text("New List Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    placeholder = { Text("e.g. Science Fun, Music...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            val title = newPlaylistTitle.trim()
                            showCreateInPlayer = false
                            viewModel.createPlaylistAndAddVideo(title) { success ->
                                showAddToPlaylistDialog = false
                                toastMessage = if (success) "Created '$title' and saved video!" else "Failed to create"
                            }
                        }
                    },
                    enabled = newPlaylistTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Create & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInPlayer = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Toddler Restart Confirmation Dialog
    if (showToddlerRestartDialog) {
        AlertDialog(
            onDismissRequest = { showToddlerRestartDialog = false },
            title = { Text("Restart Video?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to play this video again from the beginning?") },
            confirmButton = {
                Button(
                    onClick = {
                        showToddlerRestartDialog = false
                        playerRestartKey += 1
                        toastMessage = "Video restarted from beginning"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Restart", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showToddlerRestartDialog = false }) {
                    Text("Keep Watching")
                }
            }
        )
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }
}

@Composable
fun YouTubePlayerComposable(
    videoId: String,
    isPaused: Boolean = false,
    controls: Boolean = true,
    modifier: Modifier = Modifier
) {
    YouTubeIFramePlayer(
        videoId = videoId,
        controls = controls,
        startSeconds = 0,
        isPaused = isPaused,
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerComposable(
    mediaUrl: String,
    isPaused: Boolean = false,
    useController: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) exoPlayer.pause() else exoPlayer.play()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                this.useController = useController
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun ScreenTimeLimitExceededView(
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF4361EE).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Screen Time Reached",
                tint = Color(0xFF4CC9F0),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Screen Time is Up!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "You've reached your daily watch limit for today. Great job! Time to play, read, or get some rest.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onBackClick,
            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
        ) {
            Text("Back to Explore")
        }
    }
}

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

@Composable
private fun InattentionPausedOverlay(
    awaySeconds: Int,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xE60B1120))
            .clickable(onClick = onResumeClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Are you still watching?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Video stopped after ${awaySeconds}s with no viewer detected.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onResumeClick,
            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
        ) {
            Text("Resume Watching", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
