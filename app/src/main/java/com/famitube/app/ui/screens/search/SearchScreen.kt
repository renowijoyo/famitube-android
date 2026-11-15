package com.famitube.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.data.model.SearchSettings
import com.famitube.app.data.model.Video
import com.famitube.app.data.model.YouTubeSearchResult
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: FamiTubeRepository,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Curated, 1 = YouTube Search
    var searchResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var ytResults by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    var ytSettings by remember { mutableStateOf<SearchSettings?>(null) }
    var ytError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            statusMessage = null
        }
    }

    fun performSearch(text: String, tab: Int = selectedTab) {
        searchJob?.cancel()
        if (text.isBlank()) {
            searchResults = emptyList()
            ytResults = emptyList()
            ytError = null
            isSearching = false
            return
        }

        searchJob = scope.launch {
            delay(350) // debounce
            isSearching = true
            ytError = null
            if (tab == 0) {
                val res = repository.getVideos(search = text.trim())
                searchResults = res.getOrDefault(emptyList())
            } else {
                val res = repository.searchYouTube(text.trim())
                if (res.isSuccess) {
                    val data = res.getOrNull()
                    ytResults = data?.results ?: emptyList()
                    ytSettings = data?.searchSettings
                } else {
                    ytResults = emptyList()
                    ytError = res.exceptionOrNull()?.localizedMessage ?: "YouTube search unavailable"
                }
            }
            isSearching = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = {
                            query = it
                            performSearch(it, selectedTab)
                        },
                        placeholder = {
                            Text(
                                if (selectedTab == 0) "Search safe cartoons, science, songs..."
                                else "Search YouTube (parent supervised)..."
                            )
                        },
                        singleLine = true,
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
                                    performSearch("", selectedTab)
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (repository.isLoggedIn()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = PinkPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            if (query.isNotBlank()) performSearch(query, 0)
                        },
                        text = { Text("Curated Safe", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            if (query.isNotBlank()) performSearch(query, 1)
                        },
                        text = { Text("YouTube Search", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PinkPrimary
                    )
                } else if (selectedTab == 0) {
                    if (query.isNotBlank() && searchResults.isEmpty()) {
                        EmptySearchState(
                            title = "No matching videos found",
                            subtitle = "Try searching for animals, cartoons, learning, or music."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults) { video ->
                                VideoCard(video = video, onClick = { onVideoClick(video.id) })
                            }
                        }
                    }
                } else {
                    // YouTube Search Tab
                    if (ytError != null) {
                        EmptySearchState(
                            title = "YouTube Search Unavailable",
                            subtitle = ytError!!
                        )
                    } else if (query.isNotBlank() && ytResults.isEmpty()) {
                        EmptySearchState(
                            title = "No YouTube results found",
                            subtitle = "Try a different search phrase."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                                                "$quotaText • Parent approval required"
                                            } else {
                                                quotaText
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }

                            items(ytResults, key = { it.youtubeVideoId }) { item ->
                                YouTubeSearchResultCard(
                                    item = item,
                                    onClick = {
                                        scope.launch {
                                            val watchRes = repository.requestOrWatchYouTube(item)
                                            val data = watchRes.getOrNull()
                                            if (data != null) {
                                                if (data.allowed) {
                                                    val targetId = item.curatedVideoId ?: "yt:${item.youtubeVideoId}"
                                                    onVideoClick(targetId)
                                                } else {
                                                    statusMessage = data.message ?: "Request sent to parent!"
                                                    performSearch(query, 1)
                                                }
                                            } else {
                                                statusMessage = watchRes.exceptionOrNull()?.localizedMessage
                                                    ?: "Could not open video"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun YouTubeSearchResultCard(
    item: YouTubeSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
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
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.channelTitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val badgeText = when {
                item.isCurated -> "Curated"
                item.approvalStatus == "APPROVED" || item.inHistory -> "Approved"
                item.approvalStatus == "PENDING" -> "Pending Parent"
                item.approvalStatus == "REJECTED" -> "Declined"
                else -> null
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val badgeColor = when (badgeText) {
                    "Curated", "Approved" -> Color(0xFF4CAF50)
                    "Pending Parent" -> Color(0xFFFFB74D)
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
