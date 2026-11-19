package com.famitube.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.ui.components.CategoryChip
import com.famitube.app.ui.components.FamiTubeTopBar
import com.famitube.app.ui.components.ParentalGateDialog
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.navigation.Screen
import com.famitube.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToBadges: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val starBalance by (viewModel.starBalance?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val ageFilter by viewModel.ageFilterFlow.collectAsState()
    val isSimpleMode = ageFilter == "TODDLER" || ageFilter == "YOUNG_KIDS"

    LaunchedEffect(Unit) {
        viewModel.updateRemainingTime()
    }

    Scaffold(
        topBar = {
            FamiTubeTopBar(
                remainingMinutes = state.remainingMinutes,
                starBalance = starBalance,
                onStarsClick = onNavigateToBadges,
                onProfileClick = onNavigateToProfile,
                onSearchClick = onSearchClick,
                showSearchButton = !isSimpleMode,
                onBrowserClick = onNavigateToBrowser,
                showBrowserButton = true
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (state.errorMessage != null && state.categories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Cannot connect to FamiTube Server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage ?: "Check backend status or server URL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadData() },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            } else if (isSimpleMode) {
                SimpleKidHomeContent(
                    state = state,
                    isToddler = ageFilter == "TODDLER",
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onVideoClick = onVideoClick
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 1. Intentional Search-First Prompt Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = CyanAccent.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "What do you want to watch?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Search for a topic, pick a channel, or open one of your playlists.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(onClick = onSearchClick)
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = BlueSecondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Search safe videos or request with ⭐...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 2. Quick Intentional Destinations
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickDestinationTile(
                                title = "Channels",
                                icon = Icons.Default.Tv,
                                accent = CyanAccent,
                                onClick = { onNavigateTab(Screen.Channels.route) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickDestinationTile(
                                title = "Playlists",
                                icon = Icons.Default.VideoLibrary,
                                accent = PinkPrimary,
                                onClick = { onNavigateTab(Screen.Playlists.route) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickDestinationTile(
                                title = "Favorites",
                                icon = Icons.Default.Favorite,
                                accent = MintSuccess,
                                onClick = { onNavigateTab(Screen.Favorites.route) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickDestinationTile(
                                title = "Badges",
                                icon = Icons.Default.EmojiEvents,
                                accent = YellowWarning,
                                onClick = onNavigateToBadges,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Deliberate Topic Picker
                    if (state.categories.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pick a Topic",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (state.selectedCategory != null) {
                                    TextButton(onClick = { viewModel.selectCategory(state.selectedCategory!!) }) {
                                        Text("Clear", color = PinkPrimary)
                                    }
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.categories) { category ->
                                    CategoryChip(
                                        name = category.name,
                                        isSelected = state.selectedCategory == category.name,
                                        onClick = { viewModel.selectCategory(category.name) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Only show videos if the child deliberately chose a topic
                    val selectedCat = state.selectedCategory
                    if (selectedCat == null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "🎯",
                                        fontSize = 32.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No endless feed here!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Use Search or pick a Topic above when you know what you want to watch.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (state.isLoadingTopicVideos) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PinkPrimary)
                            }
                        }
                    } else if (state.videos.isEmpty()) {
                        item {
                            Text(
                                text = "No videos in $selectedCat right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = selectedCat,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                            )
                        }
                        items(state.videos) { video ->
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
}

@Composable
private fun QuickDestinationTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun SimpleKidHomeContent(
    state: HomeUiState,
    isToddler: Boolean,
    onSelectCategory: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val selectedCat = state.selectedCategory
    val tileColors = listOf(PinkPrimary, CyanAccent, MintSuccess, YellowWarning, BlueSecondary)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedCat == null) {
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = if (isToddler) "Pick a Picture!" else "Pick a Topic!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap a topic below to see safe videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val rows = state.categories.chunked(2)
            items(rows.size) { rowIndex ->
                val pair = rows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEachIndexed { colIndex, category ->
                        val accent = tileColors[(rowIndex * 2 + colIndex) % tileColors.size]
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isToddler) 136.dp else 118.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.5.dp,
                                    color = accent.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable { onSelectCategory(category.name) }
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = topicEmojiForCategory(category.name),
                                fontSize = if (isToddler) 38.sp else 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSelectCategory(selectedCat) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "All Topics",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Topics", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${topicEmojiForCategory(selectedCat)} $selectedCat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (state.isLoadingTopicVideos) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PinkPrimary)
                    }
                }
            } else if (state.videos.isEmpty()) {
                item {
                    Text(
                        text = "No videos for this topic yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.videos) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video.id) }
                    )
                }
            }
        }
    }
}

private fun topicEmojiForCategory(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("animal") || lower.contains("nature") -> "🦁"
        lower.contains("cartoon") || lower.contains("fun") -> "🎨"
        lower.contains("craft") || lower.contains("art") -> "✂️"
        lower.contains("discovery") || lower.contains("world") -> "🌍"
        lower.contains("yoga") || lower.contains("active") -> "🤸"
        lower.contains("learn") || lower.contains("abc") -> "📚"
        lower.contains("music") || lower.contains("rhyme") -> "🎵"
        lower.contains("science") || lower.contains("space") -> "🚀"
        lower.contains("math") || lower.contains("coding") -> "🔢"
        lower.contains("stor") || lower.contains("read") -> "📖"
        else -> "🎬"
    }
}
