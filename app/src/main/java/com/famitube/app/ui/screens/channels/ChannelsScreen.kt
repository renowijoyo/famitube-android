package com.famitube.app.ui.screens.channels

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.data.model.Channel
import com.famitube.app.data.model.ChannelType
import com.famitube.app.data.model.YouTubeChannelDetailData
import com.famitube.app.data.model.YouTubeChannelSearchResult
import com.famitube.app.data.model.YouTubePlaylistDetailData
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.theme.CyanAccent
import com.famitube.app.ui.theme.GreenSuccess
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import com.famitube.app.ui.theme.SurfaceLightDark
import com.famitube.app.ui.theme.YellowWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    repository: FamiTubeRepository,
    onChannelClick: (Channel) -> Unit,
    onVideoClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var savedChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var ytSubscribedChannels by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var activeYtChannelDetail by remember { mutableStateOf<YouTubeChannelDetailData?>(null) }
    var activeYtChannelTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Playlists
    var activeYtPlaylistDetail by remember { mutableStateOf<YouTubePlaylistDetailData?>(null) }
    var isLoadingYtChannel by remember { mutableStateOf(false) }
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var topics by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()
    val scope = rememberCoroutineScope()

    fun loadChannels() {
        scope.launch {
            isLoading = true
            val savedDef = async { repository.getKidSavedChannels() }
            val ytSubsDef = async { repository.getKidYouTubeSubscriptions() }
            val allDef = async { repository.getChannels() }
            val catsDef = async { repository.getCategories() }

            val savedList = savedDef.await().getOrDefault(emptyList())
            val ytList = ytSubsDef.await().getOrDefault(emptyList())
            val allList = allDef.await().getOrDefault(emptyList())
            val cats = catsDef.await().getOrDefault(emptyList())
                .map { it.name }
                .filter { it.isNotBlank() && it != "All Videos" }

            savedChannels = savedList
            ytSubscribedChannels = ytList
            allChannels = allList
            topics = (cats + allList.map { it.category })
                .filter { it.isNotBlank() }
                .distinct()
            isLoading = false
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
                Toast.makeText(
                    context,
                    err.localizedMessage ?: "Could not load channel $norm",
                    Toast.LENGTH_SHORT
                ).show()
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
                Toast.makeText(
                    context,
                    err.localizedMessage ?: "Could not load playlist",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(ageFilter) {
        loadChannels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Channels", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showAddChannelDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Subscribe to a channel",
                            tint = PinkPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Subscribe", color = PinkPrimary, fontWeight = FontWeight.Bold)
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
            if (isLoading || isLoadingYtChannel) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (activeYtChannelDetail != null) {
                val detail = activeYtChannelDetail!!
                val ch = detail.channel
                val chVideos = detail.videos
                val chPlaylists = detail.playlists
                val isTrusted = ch.trustLevel == "TRUSTED"
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
                                Text(if (activeYtPlaylistDetail != null) "← Back to Playlists" else "← Back to My Channels")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.unsubscribeKidYouTubeChannel(ch.handle)
                                        ytSubscribedChannels = ytSubscribedChannels.filterNot {
                                            it.handle.equals(ch.handle, ignoreCase = true)
                                        }
                                        activeYtChannelDetail = null
                                        activeYtPlaylistDetail = null
                                        Toast.makeText(context, "Unsubscribed from ${ch.handle}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Unsubscribe")
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceDark)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ch.handle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        scope.launch {
                                            val watchRes = repository.requestOrWatchYouTube(item)
                                            val data = watchRes.getOrNull()
                                            if (data != null) {
                                                if (data.allowed) {
                                                    val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                    onVideoClick?.invoke(targetId)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        data.message ?: "Request sent to parent!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    openYtPlaylistVideos(plDetail.playlistId, ch.handle)
                                                }
                                            }
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(70.dp)
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
                                            .size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val badgeText = when {
                                        item.isCurated -> "Curated"
                                        item.approvalStatus == "APPROVED" || item.inHistory || isTrusted -> "✓ Auto-Approved"
                                        item.approvalStatus == "PENDING" -> "Pending Parent"
                                        item.approvalStatus == "REJECTED" -> "Declined"
                                        else -> "Watch / Request"
                                    }
                                    val badgeColor = when {
                                        badgeText == "Curated" || badgeText == "✓ Auto-Approved" -> GreenSuccess
                                        badgeText == "Pending Parent" -> YellowWarning
                                        else -> CyanAccent
                                    }
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    } else if (activeYtChannelTab == 1) {
                        if (chPlaylists.isEmpty()) {
                            item {
                                Text(
                                    text = "No playlists found for this channel.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(chPlaylists, key = { "ch_pl_${it.playlistId}" }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SurfaceDark)
                                        .clickable { openYtPlaylistVideos(pl.playlistId, ch.handle) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(70.dp)
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
                                            text = "Open Playlist",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(chVideos, key = { "ch_yt_${it.youtubeVideoId}" }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        scope.launch {
                                            val watchRes = repository.requestOrWatchYouTube(item)
                                            val data = watchRes.getOrNull()
                                            if (data != null) {
                                                if (data.allowed) {
                                                    val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                    onVideoClick?.invoke(targetId)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        data.message ?: "Request sent to parent!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    openYtChannelVideos(ch.handle)
                                                }
                                            }
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(70.dp)
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
                                            .size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val badgeText = when {
                                        item.isCurated -> "Curated"
                                        item.approvalStatus == "APPROVED" || item.inHistory || isTrusted -> "✓ Auto-Approved"
                                        item.approvalStatus == "PENDING" -> "Pending Parent"
                                        item.approvalStatus == "REJECTED" -> "Declined"
                                        else -> "Watch / Request"
                                    }
                                    val badgeColor = when {
                                        badgeText == "Curated" || badgeText == "✓ Auto-Approved" -> GreenSuccess
                                        badgeText == "Pending Parent" -> YellowWarning
                                        else -> CyanAccent
                                    }
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
            } else if (savedChannels.isEmpty() && ytSubscribedChannels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "📺", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Subscribed Channels Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pick a topic and subscribe to your favorite safe channels, or subscribe to @YouTube channels from Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showAddChannelDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subscribe to a channel", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (ytSubscribedChannels.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column {
                                Text(
                                    text = "My @YouTube Subscriptions (${ytSubscribedChannels.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YellowWarning
                                )
                                Text(
                                    text = "Parent-Trusted @channels play immediately; Supervised @channels check parent approval.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        items(ytSubscribedChannels, key = { "ytsub_${it.handle}" }, span = { GridItemSpan(2) }) { ytCh ->
                            val isTrusted = ytCh.trustLevel == "TRUSTED"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openYtChannelVideos(ytCh.handle) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ytCh.avatarUrl,
                                        contentDescription = ytCh.handle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ytCh.handle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = CyanAccent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isTrusted) GreenSuccess.copy(alpha = 0.18f)
                                                        else YellowWarning.copy(alpha = 0.18f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isTrusted) "✓ TRUSTED" else "SUPERVISED",
                                                    color = if (isTrusted) GreenSuccess else YellowWarning,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ytCh.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.unsubscribeKidYouTubeChannel(ytCh.handle)
                                                ytSubscribedChannels = ytSubscribedChannels.filterNot {
                                                    it.handle.equals(ytCh.handle, ignoreCase = true)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Unsubscribe",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (savedChannels.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                text = "Curated Safe Channels (${savedChannels.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenSuccess,
                                modifier = Modifier.padding(top = if (ytSubscribedChannels.isNotEmpty()) 6.dp else 0.dp)
                            )
                        }
                        items(savedChannels, key = { it.id }, span = { GridItemSpan(2) }) { channel ->
                            val isLiveTv = channel.channelType == ChannelType.LIVE_TV
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChannelClick(channel) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = channel.avatarUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isLiveTv) PinkPrimary.copy(alpha = 0.2f) else Color(0xFF4CC9F0).copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isLiveTv) "LIVE TV" else "CURATED",
                                                    color = if (isLiveTv) PinkPrimary else Color(0xFF4CC9F0),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${channel.category} • ${channel.videoCount} videos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val res = repository.removeKidSavedChannel(channel.id)
                                                res.onSuccess {
                                                    savedChannels = savedChannels.filterNot { it.id == channel.id }
                                                }.onFailure { err ->
                                                    Toast.makeText(
                                                        context,
                                                        err.message ?: "Failed to remove channel",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove channel",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
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

    if (showAddChannelDialog) {
        AddChannelByTopicDialog(
            topics = topics,
            allChannels = allChannels,
            savedChannelIds = savedChannels.map { it.id }.toSet(),
            onChannelClick = onChannelClick,
            onAddChannel = { channel ->
                scope.launch {
                    val res = repository.addKidSavedChannel(channel.id)
                    res.onSuccess {
                        if (savedChannels.none { it.id == channel.id }) {
                            savedChannels = listOf(channel) + savedChannels
                        }
                        Toast.makeText(context, "Subscribed to ${channel.name}", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            err.message ?: "Failed to add channel",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { showAddChannelDialog = false }
        )
    }
}

@Composable
private fun AddChannelByTopicDialog(
    topics: List<String>,
    allChannels: List<Channel>,
    savedChannelIds: Set<String>,
    onAddChannel: (Channel) -> Unit,
    onDismiss: () -> Unit,
    onChannelClick: ((Channel) -> Unit)? = null
) {
    var topicQuery by remember { mutableStateOf("") }
    var selectedTopic by remember { mutableStateOf<String?>(null) }

    val matchingTopics = remember(topics, topicQuery) {
        val q = topicQuery.trim().lowercase()
        if (q.isEmpty()) topics
        else topics.filter { it.lowercase().contains(q) }
    }

    val topicChannels = remember(allChannels, selectedTopic) {
        if (selectedTopic == null) emptyList()
        else allChannels.filter { it.category.equals(selectedTopic, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTopic == null) "Choose a Topic" else selectedTopic!!,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Always-visible topic search bar so the child can search a topic anytime
                OutlinedTextField(
                    value = topicQuery,
                    onValueChange = {
                        topicQuery = it
                        // When typing a new search while viewing a topic, show matching topics
                        if (it.isNotBlank() && selectedTopic != null && !selectedTopic!!.contains(it, ignoreCase = true)) {
                            selectedTopic = null
                        }
                    },
                    placeholder = { Text("Search topics...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (topicQuery.isNotEmpty()) {
                            IconButton(onClick = { topicQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedTopic == null) {
                    Text(
                        text = "Pick a topic to see channels:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (matchingTopics.isEmpty()) {
                        Text(
                            text = "No topics match \"$topicQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(matchingTopics) { topic ->
                                val count = allChannels.count { it.category.equals(topic, ignoreCase = true) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTopic = topic },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(topic, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "$count channels ›",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PinkPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Quick topic chips / back to topics while still keeping search bar above
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { selectedTopic = null },
                                label = { Text("← All Topics", fontSize = 11.sp) }
                            )
                        }
                        items(matchingTopics) { topic ->
                            FilterChip(
                                selected = topic.equals(selectedTopic, ignoreCase = true),
                                onClick = { selectedTopic = topic },
                                label = { Text(topic, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (topicChannels.isEmpty()) {
                        Text(
                            text = "No channels available in this topic for your age tier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(topicChannels, key = { it.id }) { ch ->
                                val isAdded = savedChannelIds.contains(ch.id)
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.clickable {
                                        onDismiss()
                                        onChannelClick?.invoke(ch)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ch.avatarUrl,
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.name,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${ch.videoCount} videos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isAdded) {
                                            Text(
                                                text = "Subscribed ✓",
                                                color = GreenSuccess,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Button(
                                                onClick = { onAddChannel(ch) },
                                                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Subscribe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ChannelGridCard(
    channel: Channel,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove channel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isLiveTv = channel.channelType == ChannelType.LIVE_TV
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLiveTv) PinkPrimary.copy(alpha = 0.2f) else Color(0xFF4CC9F0).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isLiveTv) "🔴 LIVE TV" else "▶️ ON-DEMAND",
                        color = if (isLiveTv) PinkPrimary else Color(0xFF4CC9F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Channel",
                            tint = Color(0xFF4CC9F0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = PinkPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isLiveTv) "24H Live Schedule" else "${channel.videoCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
