package com.famitube.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.model.SourceType
import com.famitube.app.data.model.ToddlerTvChannelLoopDto
import com.famitube.app.data.model.Video
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import com.famitube.app.ui.theme.SurfaceLightDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ToddlerTvPhase {
    TUNING,
    PLAYING,
    BREATHER,
    SIGNOFF
}

private val TODDLER_FALLBACK_EXCLUDED_TAGS = setOf(
    "pacing:high-energy",
    "format:short-form",
    "commercial:toy-unboxing",
    "advisory:mild-peril",
    "advisory:slapstick"
)

private fun channelEmojiForTopic(topic: String): String = when (topic) {
    "Music & Rhymes" -> "🎵"
    "Animals & Nature" -> "🐶"
    "Learning & ABCs" -> "🔤"
    "Art & Crafts" -> "🎨"
    "Kids Yoga & Active" -> "🤸"
    "Stories & Reading" -> "📖"
    "Cartoons & Fun" -> "🧸"
    else -> "📺"
}

private fun pickWindDownIndex(videos: List<Video>, currentIndex: Int): Int {
    if (videos.size <= 1) return 0
    val calmIdx = videos.indexOfFirst { v ->
        v.tags.any { it == "pacing:calm" || it == "context:bedtime-safe" }
    }
    if (calmIdx >= 0 && calmIdx != currentIndex) return calmIdx
    return (currentIndex + 1) % videos.size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToddlerTvPlayerScreen(
    repository: FamiTubeRepository,
    topic: String,
    onBackClick: () -> Unit
) {
    val prefs = remember { repository.getPreferences() }
    val scope = rememberCoroutineScope()
    val favoriteIds by repository.favoriteIdsFlow.collectAsState()

    var isLoading by remember { mutableStateOf(true) }
    var channelLoop by remember { mutableStateOf<ToddlerTvChannelLoopDto?>(null) }
    var isCoolingDown by remember { mutableStateOf(prefs.isToddlerCoolingDown()) }
    var cooldownRemainingMin by remember { mutableIntStateOf(prefs.getToddlerCooldownRemainingMinutes()) }

    var phase by remember { mutableStateOf(ToddlerTvPhase.TUNING) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var elapsedInVideo by remember { mutableIntStateOf(0) }
    var startOffsetForCurrentSlot by remember { mutableIntStateOf(0) }
    var sessionWatchedSeconds by remember { mutableIntStateOf(prefs.toddlerSessionWatchedSeconds) }

    val context = LocalContext.current
    val activity = remember(context) { context.findToddlerActivity() }
    var isFullscreen by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(topic) {
        isLoading = true
        isCoolingDown = prefs.isToddlerCoolingDown()
        cooldownRemainingMin = prefs.getToddlerCooldownRemainingMinutes()

        val remote = repository.getToddlerTv().getOrNull()
        val matched = remote?.channels?.firstOrNull {
            it.topic.equals(topic, ignoreCase = true) ||
                it.displayTitle.equals(topic, ignoreCase = true)
        }
        val resolvedLoop = if (matched != null && matched.videos.isNotEmpty()) {
            matched
        } else {
            val fallbackVideos = repository.getVideos(category = topic, ageRating = "TODDLER")
                .getOrDefault(emptyList())
                .filter { video ->
                    video.tags.none { it in TODDLER_FALLBACK_EXCLUDED_TAGS }
                }
                .take(6)
            ToddlerTvChannelLoopDto(
                topic = topic,
                displayTitle = topic,
                emoji = channelEmojiForTopic(topic),
                videos = fallbackVideos
            )
        }
        channelLoop = resolvedLoop

        if (resolvedLoop.videos.isNotEmpty()) {
            val saved = prefs.getToddlerPlayhead(topic)
            val nowMs = System.currentTimeMillis()
            if (saved.lastLeftAtMs > 0L && (nowMs - saved.lastLeftAtMs) < 180_000L) {
                val safeIdx = saved.index.coerceIn(0, resolvedLoop.videos.lastIndex)
                val maxDur = resolvedLoop.videos[safeIdx].duration.coerceAtLeast(30)
                val safeElapsed = saved.elapsedSeconds.coerceIn(0, (maxDur - 5).coerceAtLeast(0))
                currentIndex = safeIdx
                elapsedInVideo = safeElapsed
                startOffsetForCurrentSlot = safeElapsed
            } else {
                currentIndex = saved.index.coerceIn(0, resolvedLoop.videos.lastIndex)
                elapsedInVideo = 0
                startOffsetForCurrentSlot = 0
            }
        }
        isLoading = false
    }

    // Save sticky playhead on exit
    DisposableEffect(topic) {
        onDispose {
            if (!isCoolingDown) {
                prefs.saveToddlerPlayhead(
                    topic,
                    ParentalPreferences.ToddlerPlayhead(
                        index = currentIndex,
                        elapsedSeconds = elapsedInVideo,
                        lastLeftAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    val videos = channelLoop?.videos ?: emptyList()
    val currentVideo = videos.getOrNull(currentIndex)
    val loopBudgetSeconds = (prefs.toddlerLoopMinutes.coerceAtLeast(5)) * 60

    // Phase state machine
    LaunchedEffect(phase, currentIndex, isLoading, isCoolingDown, videos.size) {
        if (isLoading || isCoolingDown || videos.isEmpty()) return@LaunchedEffect

        when (phase) {
            ToddlerTvPhase.TUNING -> {
                delay(2200L)
                phase = ToddlerTvPhase.PLAYING
            }
            ToddlerTvPhase.PLAYING -> {
                val activeVideo = videos.getOrNull(currentIndex) ?: return@LaunchedEffect
                val targetDuration = activeVideo.duration.coerceAtLeast(30)
                while (phase == ToddlerTvPhase.PLAYING) {
                    delay(1000L)
                    elapsedInVideo += 1
                    sessionWatchedSeconds += 1
                    prefs.toddlerSessionWatchedSeconds = sessionWatchedSeconds
                    prefs.addUsedWatchTime(1)

                    if (elapsedInVideo >= targetDuration) {
                        if (sessionWatchedSeconds >= loopBudgetSeconds) {
                            phase = ToddlerTvPhase.SIGNOFF
                        } else {
                            phase = ToddlerTvPhase.BREATHER
                        }
                    }
                }
            }
            ToddlerTvPhase.BREATHER -> {
                val remainingBudget = loopBudgetSeconds - sessionWatchedSeconds
                val nextIdx = if (remainingBudget <= 210) {
                    pickWindDownIndex(videos, currentIndex)
                } else {
                    (currentIndex + 1) % videos.size
                }
                delay(3500L)
                currentIndex = nextIdx
                elapsedInVideo = 0
                startOffsetForCurrentSlot = 0
                prefs.saveToddlerPlayhead(
                    topic,
                    ParentalPreferences.ToddlerPlayhead(
                        index = nextIdx,
                        elapsedSeconds = 0,
                        lastLeftAtMs = System.currentTimeMillis()
                    )
                )
                phase = ToddlerTvPhase.PLAYING
            }
            ToddlerTvPhase.SIGNOFF -> {
                prefs.startToddlerCooldown()
                prefs.toddlerSessionWatchedSeconds = 0
                isCoolingDown = true
                cooldownRemainingMin = prefs.getToddlerCooldownRemainingMinutes()
                scope.launch { repository.startRemoteToddlerCooldown() }
                delay(8000L)
                onBackClick()
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${channelLoop?.emoji ?: channelEmojiForTopic(topic)} ${channelLoop?.displayTitle ?: topic}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = PinkPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(99.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PinkPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Linear TV",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PinkPrimary
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to TV Channels",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        val loopMin = prefs.toddlerLoopMinutes.coerceAtLeast(5)
                        val watchedMin = (sessionWatchedSeconds / 60).coerceAtMost(loopMin)
                        Text(
                            text = "${watchedMin}/${loopMin}m",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFB8C0FF),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        if (currentVideo != null && !isCoolingDown && phase == ToddlerTvPhase.PLAYING) {
                            IconButton(onClick = { showRestartDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Restart Video from Beginning",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { isFullscreen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                            val isFav = currentVideo.id in favoriteIds
                            IconButton(onClick = { repository.toggleFavorite(currentVideo.id) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFav) "Remove Favorite" else "Favorite",
                                    tint = if (isFav) PinkPrimary else Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        titleContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color(0xFF0E101A)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isFullscreen) Modifier.padding(paddingValues) else Modifier)
                .background(Color(0xFF0E101A)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PinkPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading Toddler TV...", color = Color.White)
                    }
                }

                isCoolingDown && phase != ToddlerTvPhase.SIGNOFF -> {
                    ToddlerBumperCard(
                        emoji = "🌙",
                        title = "Shhh... TV is Napping",
                        subtitle = "TV will wake up in $cooldownRemainingMin minute${if (cooldownRemainingMin == 1) "" else "s"}. Time for blocks, drawing, or a story!",
                        gradient = listOf(Color(0xFF1E1B4B), Color(0xFF312E81)),
                        actionLabel = "Back to Channels",
                        onAction = onBackClick
                    )
                }

                videos.isEmpty() -> {
                    ToddlerBumperCard(
                        emoji = channelLoop?.emoji ?: "📺",
                        title = "Channel Resting",
                        subtitle = "No calm videos are ready on this channel right now.",
                        gradient = listOf(SurfaceDark, SurfaceLightDark),
                        actionLabel = "Pick Another Channel",
                        onAction = onBackClick
                    )
                }

                phase == ToddlerTvPhase.TUNING -> {
                    ToddlerBumperCard(
                        emoji = channelLoop?.emoji ?: channelEmojiForTopic(topic),
                        title = "Tuning in to ${channelLoop?.displayTitle ?: topic}...",
                        subtitle = "Sit back and enjoy calm, gentle TV",
                        gradient = listOf(Color(0xFF1A1F36), Color(0xFF282E4E)),
                        showProgress = true
                    )
                }

                phase == ToddlerTvPhase.BREATHER -> {
                    val remainingBudget = loopBudgetSeconds - sessionWatchedSeconds
                    val nextIdx = if (remainingBudget <= 210) {
                        pickWindDownIndex(videos, currentIndex)
                    } else {
                        (currentIndex + 1) % videos.size
                    }
                    val nextTitle = videos.getOrNull(nextIdx)?.title ?: "Next gentle story"
                    ToddlerBumperCard(
                        emoji = "🌿",
                        title = "Take a gentle breath...",
                        subtitle = "Up next: $nextTitle",
                        gradient = listOf(Color(0xFF132A2E), Color(0xFF1F4046)),
                        showProgress = true
                    )
                }

                phase == ToddlerTvPhase.SIGNOFF -> {
                    ToddlerBumperCard(
                        emoji = "🌙",
                        title = "Nighty-night, TV is going to nap!",
                        subtitle = "Great watching! TV will rest for ${prefs.toddlerCooldownMinutes} minutes.",
                        gradient = listOf(Color(0xFF1E1B4B), Color(0xFF2E1065)),
                        actionLabel = "All Done",
                        onAction = onBackClick
                    )
                }

                currentVideo != null -> {
                    if (isFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            key(currentVideo.id, startOffsetForCurrentSlot, restartKey) {
                                if (currentVideo.sourceType == SourceType.YOUTUBE &&
                                    !currentVideo.youtubeVideoId.isNullOrBlank()
                                ) {
                                    LockedYouTubePlayerComposable(
                                        videoId = currentVideo.youtubeVideoId,
                                        startSeconds = startOffsetForCurrentSlot.toFloat(),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    LockedExoPlayerComposable(
                                        mediaUrl = currentVideo.sourceUrl,
                                        startPositionMs = startOffsetForCurrentSlot * 1000L,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Scrub-lock shield: prevents toddler from tapping YouTube chrome or seeking ahead
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {}
                                    )
                            )

                            // Immersive floating top overlay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0xCC000000), Color.Transparent)
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = currentVideo.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                IconButton(
                                    onClick = { showRestartDialog = true },
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

                                val isFav = currentVideo.id in favoriteIds
                                IconButton(
                                    onClick = { repository.toggleFavorite(currentVideo.id) },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x88000000))
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFav) "Remove Favorite" else "Favorite",
                                        tint = if (isFav) PinkPrimary else Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.Black)
                            ) {
                                key(currentVideo.id, startOffsetForCurrentSlot, restartKey) {
                                    if (currentVideo.sourceType == SourceType.YOUTUBE &&
                                        !currentVideo.youtubeVideoId.isNullOrBlank()
                                    ) {
                                        LockedYouTubePlayerComposable(
                                            videoId = currentVideo.youtubeVideoId,
                                            startSeconds = startOffsetForCurrentSlot.toFloat(),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        LockedExoPlayerComposable(
                                            mediaUrl = currentVideo.sourceUrl,
                                            startPositionMs = startOffsetForCurrentSlot * 1000L,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Scrub-lock shield: prevents toddler from tapping YouTube chrome or seeking ahead
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

                            // Calm bottom info bar (no scrub bar, no skip ahead)
                            Surface(
                                color = SurfaceDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentVideo.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${currentVideo.channelTitle} • Calm Toddler TV Loop (${currentIndex + 1}/${videos.size})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFA5B4FC)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = { isFullscreen = true },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(SurfaceLightDark)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Fullscreen",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedButton(
                                        onClick = { showRestartDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CC9F0))
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Restart Video from Beginning", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Toddler Restart Confirmation Dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Video?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to play this video again from the beginning?") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartDialog = false
                        elapsedInVideo = 0
                        startOffsetForCurrentSlot = 0
                        restartKey += 1
                        prefs.saveToddlerPlayhead(
                            topic,
                            ParentalPreferences.ToddlerPlayhead(
                                index = currentIndex,
                                elapsedSeconds = 0,
                                lastLeftAtMs = System.currentTimeMillis()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Restart", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Keep Watching")
                }
            }
        )
    }
}

@Composable
private fun ToddlerBumperCard(
    emoji: String,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 44.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0E7FF),
                textAlign = TextAlign.Center
            )
            if (showProgress) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    color = PinkPrimary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .width(180.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(99.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private tailrec fun Context.findToddlerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findToddlerActivity()
    else -> null
}
