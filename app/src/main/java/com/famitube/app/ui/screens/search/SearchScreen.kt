package com.famitube.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.data.model.SearchSettings
import com.famitube.app.data.model.StarCosts
import com.famitube.app.data.model.Video
import com.famitube.app.data.model.YouTubeChannelDetailData
import com.famitube.app.data.model.YouTubeChannelSearchResult
import com.famitube.app.data.model.YouTubePlaylistDetailData
import com.famitube.app.data.model.YouTubeSearchResult
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CURATED_PAGE_SIZE = 5
private const val YOUTUBE_PAGE_SIZE = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: FamiTubeRepository,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var curatedResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var ytResults by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    var ytChannels by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var subscribedYtChannels by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var activeYtChannelDetail by remember { mutableStateOf<YouTubeChannelDetailData?>(null) }
    var activeYtChannelTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Playlists
    var activeYtPlaylistDetail by remember { mutableStateOf<YouTubePlaylistDetailData?>(null) }
    var isLoadingYtChannel by remember { mutableStateOf(false) }
    var curatedVisibleCount by remember { mutableIntStateOf(CURATED_PAGE_SIZE) }
    var ytVisibleCount by remember { mutableIntStateOf(YOUTUBE_PAGE_SIZE) }
    var ytSettings by remember { mutableStateOf<SearchSettings?>(null) }
    var ytError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var pendingRequestItem by remember { mutableStateOf<YouTubeSearchResult?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val starPointsManager = repository.getStarPointsManager()
    val starBalance by (starPointsManager?.starBalanceFlow?.collectAsState()
        ?: remember { mutableIntStateOf(0) })
    val favoriteIds by repository.favoriteIdsFlow.collectAsState()
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()
    val isToddler = ageFilter == "TODDLER"

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            statusMessage = null
        }
    }

    fun refreshYtSubscriptions() {
        if (!repository.isLoggedIn()) return
        scope.launch {
            subscribedYtChannels = repository.getKidYouTubeSubscriptions().getOrDefault(emptyList())
            val subSet = subscribedYtChannels.map { it.handle.lowercase() }.toSet()
            ytChannels = ytChannels.map { it.copy(isSubscribed = it.handle.lowercase() in subSet) }
            activeYtChannelDetail?.let { detail ->
                activeYtChannelDetail = detail.copy(
                    channel = detail.channel.copy(
                        isSubscribed = detail.channel.handle.lowercase() in subSet
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshYtSubscriptions()
    }

    fun toggleYtChannelSubscription(ch: YouTubeChannelSearchResult) {
        scope.launch {
            val isSub = subscribedYtChannels.any { it.handle.equals(ch.handle, ignoreCase = true) } || ch.isSubscribed
            if (isSub) {
                repository.unsubscribeKidYouTubeChannel(ch.handle)
                statusMessage = "Unsubscribed from ${ch.handle}"
            } else {
                repository.subscribeKidYouTubeChannel(ch)
                statusMessage = "Subscribed to ${ch.handle}"
            }
            refreshYtSubscriptions()
        }
    }

    fun openYtChannelVideos(handle: String) {
        val norm = if (handle.startsWith("@")) handle else "@$handle"
        scope.launch {
            isLoadingYtChannel = true
            val res = repository.getKidYouTubeChannelVideos(norm)
            isLoadingYtChannel = false
            res.onSuccess {
                activeYtChannelDetail = it
                activeYtChannelTab = 0
                activeYtPlaylistDetail = null
            }.onFailure { err ->
                statusMessage = err.localizedMessage ?: "Could not load channel $norm"
            }
        }
    }

    fun openYtPlaylistVideos(playlistId: String, handle: String) {
        scope.launch {
            isLoadingYtChannel = true
            val res = repository.getKidYouTubePlaylistVideos(playlistId, handle)
            isLoadingYtChannel = false
            res.onSuccess {
                activeYtPlaylistDetail = it
            }.onFailure { err ->
                statusMessage = err.localizedMessage ?: "Could not load playlist"
            }
        }
    }

    fun performSearch(text: String, immediate: Boolean = false) {
        searchJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            curatedResults = emptyList()
            ytResults = emptyList()
            ytChannels = emptyList()
            activeYtChannelDetail = null
            ytError = null
            curatedVisibleCount = CURATED_PAGE_SIZE
            ytVisibleCount = YOUTUBE_PAGE_SIZE
            isSearching = false
            return
        }

        searchJob = scope.launch {
            if (!immediate) delay(350)
            isSearching = true
            ytError = null
            curatedVisibleCount = CURATED_PAGE_SIZE
            ytVisibleCount = YOUTUBE_PAGE_SIZE

            val ageFilter = repository.getPreferences().selectedAgeFilter
            val curatedDeferred = async {
                repository.getVideos(search = trimmed, ageRating = ageFilter)
            }
            val ytDeferred = if (repository.isLoggedIn()) {
                async { repository.searchYouTube(trimmed) }
            } else {
                null
            }

            val curatedRes = curatedDeferred.await()
            curatedResults = curatedRes.getOrDefault(emptyList())

            if (ytDeferred != null) {
                val ytRes = ytDeferred.await()
                if (ytRes.isSuccess) {
                    val data = ytRes.getOrNull()
                    ytResults = data?.results ?: emptyList()
                    ytChannels = data?.channels ?: emptyList()
                    activeYtChannelDetail = null
                    ytSettings = data?.searchSettings
                } else {
                    ytResults = emptyList()
                    ytChannels = emptyList()
                    ytError = ytRes.exceptionOrNull()?.localizedMessage ?: "YouTube search unavailable"
                }
            } else {
                ytResults = emptyList()
                ytChannels = emptyList()
            }

            isSearching = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isToddler) {
                        Text(
                            text = "Search Unavailable",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        TextField(
                            value = query,
                            onValueChange = {
                                query = it
                                performSearch(it, immediate = false)
                            },
                            placeholder = {
                                Text("Search curated or YouTube videos...")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    performSearch(query, immediate = true)
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        query = ""
                                        performSearch("", immediate = true)
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (!isToddler) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(YellowWarning.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "⭐ $starBalance",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                isToddler -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🔒", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Search is for Young & Older Kids",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toddlers explore safe videos using picture topics on the Home screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                        ) {
                            Text("Back to Topics", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                isSearching || isLoadingYtChannel -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PinkPrimary
                    )
                }
                activeYtChannelDetail != null -> {
                    val detail = activeYtChannelDetail!!
                    val ch = detail.channel
                    val chVideos = detail.videos
                    val chPlaylists = detail.playlists
                    val isSub = subscribedYtChannels.any { it.handle.equals(ch.handle, ignoreCase = true) } || ch.isSubscribed
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (activeYtPlaylistDetail != null) {
                                            activeYtPlaylistDetail = null
                                        } else {
                                            activeYtChannelDetail = null
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (activeYtPlaylistDetail != null) "← Back to Playlists" else "← Back to Search")
                                }
                                Button(
                                    onClick = { toggleYtChannelSubscription(ch) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSub) SurfaceLightDark else PinkPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (isSub) "Subscribed" else "Subscribe",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ch.avatarUrl,
                                    contentDescription = ch.handle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = ch.handle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CyanAccent
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val isTrusted = ch.trustLevel == "TRUSTED"
                                        Text(
                                            text = if (isTrusted) "✓ Parent-Trusted" else "Supervised",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTrusted) GreenSuccess else YellowWarning
                                        )
                                    }
                                    Text(
                                        text = "${ch.title} • ${chVideos.size} videos • ${chPlaylists.size} playlists",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item {
                            TabRow(selectedTabIndex = if (activeYtPlaylistDetail != null) 1 else activeYtChannelTab) {
                                Tab(
                                    selected = activeYtPlaylistDetail == null && activeYtChannelTab == 0,
                                    onClick = {
                                        activeYtChannelTab = 0
                                        activeYtPlaylistDetail = null
                                    },
                                    text = { Text("Videos (${chVideos.size})", fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = activeYtPlaylistDetail != null || activeYtChannelTab == 1,
                                    onClick = {
                                        activeYtChannelTab = 1
                                        activeYtPlaylistDetail = null
                                    },
                                    text = { Text("Playlists (${chPlaylists.size})", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        if (activeYtPlaylistDetail != null) {
                            val plDetail = activeYtPlaylistDetail!!
                            item {
                                Text(
                                    text = "Playlist: ${plDetail.title} (${plDetail.videos.size} videos)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YellowWarning
                                )
                            }
                            items(plDetail.videos, key = { "pl_yt_${it.youtubeVideoId}" }) { item ->
                                val needsApprovalRequest = ytSettings?.searchRequireApproval == true &&
                                    !item.isCurated &&
                                    item.approvalStatus != "APPROVED" &&
                                    !item.inHistory &&
                                    item.approvalStatus != "PENDING"

                                YouTubeSearchResultCard(
                                    item = item,
                                    isFavorite = (item.youtubeVideoId?.let { "yt:$it" } in favoriteIds || item.id in favoriteIds),
                                    showRequestCost = needsApprovalRequest,
                                    onClick = {
                                        if (needsApprovalRequest) {
                                            if (starPointsManager != null && !starPointsManager.canAfford(StarCosts.VIDEO_REQUEST)) {
                                                statusMessage = "Need ${StarCosts.VIDEO_REQUEST} ⭐ to request a video! Earn stars from Badges or ask a parent."
                                            } else {
                                                pendingRequestItem = item
                                            }
                                        } else {
                                            scope.launch {
                                                val watchRes = repository.requestOrWatchYouTube(item)
                                                val data = watchRes.getOrNull()
                                                if (data != null) {
                                                    if (data.allowed) {
                                                        val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                        onVideoClick(targetId)
                                                    } else {
                                                        statusMessage = data.message ?: "Request sent to parent!"
                                                        openYtPlaylistVideos(plDetail.playlistId, ch.handle)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else if (activeYtChannelTab == 1) {
                            if (chPlaylists.isEmpty()) {
                                item {
                                    Text(
                                        text = "No playlists found for this channel.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                items(chPlaylists, key = { "ch_pl_${it.playlistId}" }) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { openYtPlaylistVideos(pl.playlistId, ch.handle) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(128.dp)
                                                .height(76.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(SurfaceLightDark)
                                        ) {
                                            AsyncImage(
                                                model = pl.thumbnailUrl,
                                                contentDescription = pl.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.75f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${pl.videoCount} videos",
                                                    fontSize = 10.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tap to view playlist videos",
                                                fontSize = 11.sp,
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(chVideos, key = { "ch_yt_${it.youtubeVideoId}" }) { item ->
                                val needsApprovalRequest = ytSettings?.searchRequireApproval == true &&
                                    !item.isCurated &&
                                    item.approvalStatus != "APPROVED" &&
                                    !item.inHistory &&
                                    item.approvalStatus != "PENDING"

                                YouTubeSearchResultCard(
                                    item = item,
                                    isFavorite = (item.youtubeVideoId?.let { "yt:$it" } in favoriteIds || item.id in favoriteIds),
                                    showRequestCost = needsApprovalRequest,
                                    onClick = {
                                        if (needsApprovalRequest) {
                                            if (starPointsManager != null && !starPointsManager.canAfford(StarCosts.VIDEO_REQUEST)) {
                                                statusMessage = "Need ${StarCosts.VIDEO_REQUEST} ⭐ to request a video! Earn stars from Badges or ask a parent."
                                            } else {
                                                pendingRequestItem = item
                                            }
                                        } else {
                                            scope.launch {
                                                val watchRes = repository.requestOrWatchYouTube(item)
                                                val data = watchRes.getOrNull()
                                                if (data != null) {
                                                    if (data.allowed) {
                                                        val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                        onVideoClick(targetId)
                                                    } else {
                                                        statusMessage = data.message ?: "Request sent to parent!"
                                                        openYtChannelVideos(ch.handle)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                query.isBlank() -> {
                    // Initial page is clear before search. Nothing before search.
                    Box(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    val visibleCurated = curatedResults.take(curatedVisibleCount)
                    val visibleYt = ytResults.take(ytVisibleCount)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ==========================================
                        // PART 1: CURATED SAFE VIDEOS (5 best match + Show More)
                        // ==========================================
                        item {
                            SearchSectionHeader(
                                title = "Curated Safe Videos",
                                countText = "${visibleCurated.size} of ${curatedResults.size}",
                                accentColor = MintSuccess
                            )
                        }

                        if (curatedResults.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching curated videos found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        } else {
                            items(visibleCurated, key = { "curated_${it.id}" }) { video ->
                                VideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video.id) },
                                    isFavorite = video.id in favoriteIds,
                                    onChannelClick = onChannelClick
                                )
                            }

                            if (curatedResults.size > curatedVisibleCount) {
                                item {
                                    OutlinedButton(
                                        onClick = { curatedVisibleCount += CURATED_PAGE_SIZE },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Show More Curated (${curatedResults.size - curatedVisibleCount} more)",
                                            color = MintSuccess,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // PART 2: YOUTUBE CHANNELS (@) & VIDEOS
                        // ==========================================
                        if (repository.isLoggedIn()) {
                            if (ytChannels.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SearchSectionHeader(
                                        title = "YouTube Channels (@)",
                                        countText = "${ytChannels.size} channels",
                                        accentColor = PinkPrimary
                                    )
                                }
                                items(ytChannels, key = { "yt_chan_${it.handle}" }) { ch ->
                                    val isSub = subscribedYtChannels.any { it.handle.equals(ch.handle, ignoreCase = true) } || ch.isSubscribed
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { openYtChannelVideos(ch.handle) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ch.avatarUrl,
                                            contentDescription = ch.handle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.handle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = CyanAccent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = ch.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { toggleYtChannelSubscription(ch) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSub) SurfaceLightDark else PinkPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isSub) "Subscribed" else "Subscribe",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                SearchSectionHeader(
                                    title = "YouTube Videos",
                                    countText = if (ytResults.isNotEmpty()) "${visibleYt.size} of ${ytResults.size}" else null,
                                    accentColor = CyanAccent
                                )
                            }

                            ytSettings?.let { settings ->
                                item {
                                    val quotaText = if (settings.searchViewLimit == 0) {
                                        "Unlimited search views"
                                    } else {
                                        "Search views used: ${settings.searchViewsUsed} / ${settings.searchViewLimit}"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceLightDark)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (settings.searchRequireApproval) {
                                                "$quotaText • Parent approval required (${StarCosts.VIDEO_REQUEST} ⭐)"
                                            } else {
                                                quotaText
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }

                            if (ytError != null) {
                                item {
                                    Text(
                                        text = ytError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFFF6B6B),
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            } else if (ytResults.isEmpty()) {
                                item {
                                    Text(
                                        text = "No YouTube results found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(visibleYt, key = { "yt_${it.youtubeVideoId}" }) { item ->
                                    val needsApprovalRequest = !isToddler && ytSettings?.searchRequireApproval == true &&
                                        !item.isCurated &&
                                        item.approvalStatus != "APPROVED" &&
                                        !item.inHistory &&
                                        item.approvalStatus != "PENDING"

                                    YouTubeSearchResultCard(
                                        item = item,
                                        isFavorite = (item.youtubeVideoId?.let { "yt:$it" } in favoriteIds || item.id in favoriteIds),
                                        showRequestCost = needsApprovalRequest,
                                        onClick = {
                                            if (needsApprovalRequest) {
                                                if (starPointsManager != null && !starPointsManager.canAfford(StarCosts.VIDEO_REQUEST)) {
                                                    statusMessage = "Need ${StarCosts.VIDEO_REQUEST} ⭐ to request a video! Earn stars from Badges or ask a parent."
                                                } else {
                                                    pendingRequestItem = item
                                                }
                                            } else {
                                                scope.launch {
                                                    val watchRes = repository.requestOrWatchYouTube(item)
                                                    val data = watchRes.getOrNull()
                                                    if (data != null) {
                                                        if (data.allowed) {
                                                            val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                            onVideoClick(targetId)
                                                        } else {
                                                            statusMessage = data.message ?: "Request sent to parent!"
                                                            performSearch(query, immediate = true)
                                                        }
                                                    } else {
                                                        statusMessage = watchRes.exceptionOrNull()?.localizedMessage
                                                            ?: "Could not open video"
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                if (ytResults.size > ytVisibleCount) {
                                    item {
                                        OutlinedButton(
                                            onClick = { ytVisibleCount += YOUTUBE_PAGE_SIZE },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Show More YouTube (${ytResults.size - ytVisibleCount} more)",
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold
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

    pendingRequestItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRequestItem = null },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Request Video (${StarCosts.VIDEO_REQUEST} ⭐)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Spend ${StarCosts.VIDEO_REQUEST} ⭐ to ask your parent to approve \"${item.title}\"?\n\nYour balance: $starBalance ⭐",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetItem = item
                        pendingRequestItem = null
                        scope.launch {
                            val watchRes = repository.requestOrWatchYouTube(targetItem)
                            val data = watchRes.getOrNull()
                            if (data != null) {
                                if (data.allowed) {
                                    val targetId = targetItem.curatedVideoId ?: "yt:${targetItem.youtubeVideoId}"
                                    onVideoClick(targetId)
                                } else {
                                    repository.spendStarsForVideoRequest(targetItem.title, StarCosts.VIDEO_REQUEST)
                                    statusMessage = "Request sent to parent! (-${StarCosts.VIDEO_REQUEST} ⭐)"
                                    performSearch(query, immediate = true)
                                }
                            } else {
                                statusMessage = watchRes.exceptionOrNull()?.localizedMessage
                                    ?: "Could not send request"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Send Request (-${StarCosts.VIDEO_REQUEST} ⭐)")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRequestItem = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    countText: String?,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor
        )
        if (!countText.isNullOrBlank()) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun YouTubeSearchResultCard(
    item: YouTubeSearchResult,
    showRequestCost: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceLightDark)
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )

            // Favorited Heart Badge on bottom-right of thumbnail
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PinkPrimary.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorited",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.channelTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val badgeText = when {
                item.isCurated -> "Curated"
                item.approvalStatus == "APPROVED" || item.inHistory -> "Approved"
                item.approvalStatus == "PENDING" -> "Pending Parent"
                item.approvalStatus == "REJECTED" -> "Declined"
                showRequestCost -> "Request (${StarCosts.VIDEO_REQUEST} ⭐)"
                else -> null
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val badgeColor = when {
                    badgeText == "Curated" || badgeText == "Approved" -> Color(0xFF4CAF50)
                    badgeText == "Pending Parent" -> Color(0xFFFFB74D)
                    badgeText.startsWith("Request") -> YellowWarning
                    else -> Color(0xFFFF6B6B)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}
