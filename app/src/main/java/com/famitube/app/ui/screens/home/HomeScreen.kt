package com.famitube.app.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.famitube.app.R
import com.famitube.app.data.model.AgeRating
import com.famitube.app.data.model.Category
import com.famitube.app.data.model.Video
import com.famitube.app.data.model.matchesCategory
import com.famitube.app.data.model.resolvedCategories
import com.famitube.app.ui.components.CategoryChip
import com.famitube.app.ui.components.FamiTubeTopBar
import com.famitube.app.ui.components.HomeNavTab
import com.famitube.app.ui.components.ParentalGateDialog
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.components.YouTubeStyleTabBar
import com.famitube.app.ui.screens.channels.ChannelsScreen
import com.famitube.app.ui.screens.games.EducationalGamesScreen
import com.famitube.app.ui.screens.stories.StoryLibraryScreen
import com.famitube.app.ui.navigation.Screen
import com.famitube.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TODDLER_CANONICAL_CHANNEL_ORDER = listOf(
    "Music & Rhymes",
    "Animals & Nature",
    "Learning & ABCs",
    "Art & Crafts",
    "Kids Yoga & Active",
    "Stories & Reading",
    "Cartoons & Fun"
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToBadges: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    unreadNotifications: Int = 0,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    onToddlerChannelClick: (String) -> Unit = {},
    onStoryClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val starBalance by (viewModel.starBalance?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val ageFilter by viewModel.ageFilterFlow.collectAsState()
    val favoriteIds by viewModel.favoriteIdsFlow.collectAsState()
    val isToddler = ageFilter == "TODDLER"
    val canSearch = !isToddler
    val prefs = remember { viewModel.repository.getPreferences() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedNavTab by remember { mutableStateOf(HomeNavTab.HOME) }
    var selectedVideosCategory by remember { mutableStateOf<String?>(null) }
    var isToddlerCoolingDown by remember(isToddler) {
        mutableStateOf(isToddler && prefs.isToddlerCoolingDown())
    }
    var cooldownRemainingMinutes by remember(isToddler) {
        mutableIntStateOf(if (isToddler) prefs.getToddlerCooldownRemainingMinutes() else 0)
    }
    var showWakeGate by remember { mutableStateOf(false) }

    val enabledToddlerTopics = prefs.toddlerEnabledTopics
    val visibleCategories = remember(state.categories, isToddler, enabledToddlerTopics) {
        if (isToddler) {
            val allowed = if (enabledToddlerTopics.isNotEmpty()) {
                enabledToddlerTopics.toSet()
            } else {
                TODDLER_CANONICAL_CHANNEL_ORDER.toSet()
            }
            state.categories
                .filter { it.name in allowed && isToddlerAppropriateCategory(it.name) }
                .sortedBy { cat ->
                    val idx = TODDLER_CANONICAL_CHANNEL_ORDER.indexOf(cat.name)
                    if (idx >= 0) idx else 999
                }
        } else {
            state.categories
        }
    }
    val visibleAllVideos = remember(state.allVideos, isToddler) {
        if (isToddler) state.allVideos.filter { video ->
            val cats = video.resolvedCategories()
            cats.any { isToddlerAppropriateCategory(it) } &&
                (video.ageRating == AgeRating.TODDLER || cats.all { isToddlerAppropriateCategory(it) })
        } else state.allVideos
    }

    LaunchedEffect(isToddler) {
        if (isToddler) {
            if (selectedNavTab != HomeNavTab.HOME && selectedNavTab != HomeNavTab.STORIES && selectedNavTab != HomeNavTab.GAMES) {
                selectedNavTab = HomeNavTab.HOME
            }
            state.selectedCategory?.let { sel ->
                if (!isToddlerAppropriateCategory(sel)) {
                    viewModel.selectCategory(sel)
                }
            }
            selectedVideosCategory?.let { sel ->
                if (!isToddlerAppropriateCategory(sel)) {
                    selectedVideosCategory = null
                }
            }
            while (true) {
                isToddlerCoolingDown = prefs.isToddlerCoolingDown()
                cooldownRemainingMinutes = prefs.getToddlerCooldownRemainingMinutes()
                delay(1000L)
            }
        } else {
            isToddlerCoolingDown = false
            cooldownRemainingMinutes = 0
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updateRemainingTime()
    }

    Scaffold(
        topBar = {
            Column {
                FamiTubeTopBar(
                    remainingMinutes = state.remainingMinutes,
                    starBalance = starBalance,
                    onStarsClick = onNavigateToBadges,
                    onProfileClick = onNavigateToProfile,
                    onSearchClick = onSearchClick,
                    showSearchButton = canSearch,
                    unreadNotifications = unreadNotifications,
                    onNotificationsClick = onNavigateToNotifications,
                    showNotificationsButton = true
                )
                YouTubeStyleTabBar(
                    selectedTab = selectedNavTab,
                    onTabSelected = { selectedNavTab = it },
                    tabs = if (isToddler) {
                        listOf(HomeNavTab.HOME, HomeNavTab.STORIES, HomeNavTab.GAMES)
                    } else {
                        listOf(HomeNavTab.HOME, HomeNavTab.VIDEOS, HomeNavTab.CHANNELS, HomeNavTab.STORIES, HomeNavTab.GAMES)
                    }
                )
            }
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
            } else if (state.errorMessage != null && state.categories.isEmpty() && state.allVideos.isEmpty()) {
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
            } else {
                when (selectedNavTab) {
                    HomeNavTab.HOME -> {
                        PictureTopicHomeContent(
                            state = state,
                            categories = visibleCategories,
                            isToddler = isToddler,
                            canSearch = canSearch,
                            favoriteIds = favoriteIds,
                            isToddlerCoolingDown = isToddlerCoolingDown,
                            cooldownRemainingMinutes = cooldownRemainingMinutes,
                            onWakeToddlerTvClick = { showWakeGate = true },
                            onToddlerChannelClick = { topic ->
                                if (isToddlerCoolingDown) {
                                    Toast.makeText(context, "Shhh... TV is napping 🌙", Toast.LENGTH_SHORT).show()
                                } else {
                                    onToddlerChannelClick(topic)
                                }
                            },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            onSearchClick = onSearchClick,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick
                        )
                    }
                    HomeNavTab.VIDEOS -> {
                        VideosTabContent(
                            videos = visibleAllVideos,
                            categories = visibleCategories,
                            isToddler = isToddler,
                            selectedCategory = selectedVideosCategory,
                            onSelectCategory = { selectedVideosCategory = it },
                            favoriteIds = favoriteIds,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick
                        )
                    }
                    HomeNavTab.CHANNELS -> {
                        ChannelsScreen(
                            repository = viewModel.repository,
                            onChannelClick = { channel -> onChannelClick(channel.id) },
                            onVideoClick = onVideoClick
                        )
                    }
                    HomeNavTab.STORIES -> {
                        StoryLibraryScreen(
                            repository = viewModel.repository,
                            currentAgeFilter = ageFilter,
                            onStoryClick = onStoryClick
                        )
                    }
                    HomeNavTab.GAMES -> {
                        EducationalGamesScreen(
                            currentAgeFilter = ageFilter,
                            starBalance = starBalance,
                            onAwardStars = { amt, desc -> viewModel.awardGameStars(amt, desc) }
                        )
                    }
                }
            }
        }
    }

    if (showWakeGate) {
        ParentalGateDialog(
            onDismiss = { showWakeGate = false },
            verifyPin = { pin -> pin == prefs.parentPin },
            onSuccess = {
                showWakeGate = false
                prefs.wakeToddlerTv()
                isToddlerCoolingDown = false
                cooldownRemainingMinutes = 0
                scope.launch { viewModel.repository.wakeToddlerTvWithPin(prefs.parentPin) }
                Toast.makeText(context, "Toddler TV is awake! ☀️", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun PictureTopicHomeContent(
    state: HomeUiState,
    categories: List<Category>,
    isToddler: Boolean,
    canSearch: Boolean,
    favoriteIds: Set<String>,
    isToddlerCoolingDown: Boolean = false,
    cooldownRemainingMinutes: Int = 0,
    onWakeToddlerTvClick: () -> Unit = {},
    onToddlerChannelClick: (String) -> Unit = {},
    onSelectCategory: (String) -> Unit,
    onSearchClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isToddler) "Toddler TV Channels 📺" else "Pick a Topic",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isToddler) "Tap a channel to tune in — calm, slow-paced loops." else "Tap a picture topic or search safe videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canSearch) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onSearchClick,
                            shape = RoundedCornerShape(99.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = BlueSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (isToddler && isToddlerCoolingDown) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🌙 Shhh... TV is Napping",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Back in $cooldownRemainingMinutes minute${if (cooldownRemainingMinutes == 1) "" else "s"}. Time to play with blocks or read a book!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE0E7FF)
                            )
                            OutlinedButton(
                                onClick = onWakeToddlerTvClick,
                                shape = RoundedCornerShape(99.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFDE68A))
                            ) {
                                Text("🔑 Parent: Wake Up TV", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            val rows = categories.chunked(2)
            items(rows.size) { rowIndex ->
                val pair = rows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEachIndexed { colIndex, category ->
                        val accent = tileColors[(rowIndex * 2 + colIndex) % tileColors.size]
                        val imgModel = topicImageForCategory(category.name, state.allVideos, isToddler)
                        val topicLabel = displayTopicLabel(category.name, isToddler)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isToddler) 154.dp else 138.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.5.dp,
                                    color = accent.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable {
                                    if (isToddler) {
                                        onToddlerChannelClick(category.name)
                                    } else {
                                        onSelectCategory(category.name)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = imgModel,
                                contentDescription = topicLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isToddler) {
                                                listOf(
                                                    Color.Black.copy(alpha = 0.0f),
                                                    Color.Black.copy(alpha = 0.14f),
                                                    Color.Black.copy(alpha = 0.78f)
                                                )
                                            } else {
                                                listOf(
                                                    Color.Black.copy(alpha = 0.08f),
                                                    Color.Black.copy(alpha = 0.35f),
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            }
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = topicEmojiForCategory(category.name, isToddler),
                                    fontSize = if (isToddler) 22.sp else 18.sp
                                )
                            }
                            Text(
                                text = topicLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
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
                        text = "${topicEmojiForCategory(selectedCat, isToddler)} ${displayTopicLabel(selectedCat, isToddler)}",
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
                        onClick = { onVideoClick(video.id) },
                        isFavorite = video.id in favoriteIds,
                        onChannelClick = onChannelClick
                    )
                }
            }
        }
    }
}

@Composable
private fun VideosTabContent(
    videos: List<Video>,
    categories: List<Category>,
    isToddler: Boolean,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    favoriteIds: Set<String>,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val filtered = remember(videos, selectedCategory) {
        if (selectedCategory == null) videos
        else videos.filter { it.matchesCategory(selectedCategory) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // Category filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        name = "All Videos",
                        isSelected = selectedCategory == null,
                        onClick = { onSelectCategory(null) }
                    )
                }
                items(categories) { cat ->
                    CategoryChip(
                        name = displayTopicLabel(cat.name, isToddler),
                        isSelected = selectedCategory == cat.name,
                        onClick = {
                            onSelectCategory(if (selectedCategory == cat.name) null else cat.name)
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategory == null) "Curated Videos (${filtered.size})" else "${displayTopicLabel(selectedCategory, isToddler)} (${filtered.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No videos found in this category.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    onClick = { onVideoClick(video.id) },
                    isFavorite = video.id in favoriteIds,
                    onChannelClick = onChannelClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun topicImageForCategory(
    name: String,
    allVideos: List<Video>,
    isToddler: Boolean = false
): Any {
    val lower = name.lowercase()
    if (isToddler) {
        when {
            lower.contains("animal") || lower.contains("nature") -> return R.drawable.topic_toddler_animals
            lower.contains("cartoon") || lower.contains("fun") -> return R.drawable.topic_toddler_cartoons
            lower.contains("craft") || lower.contains("art") -> return R.drawable.topic_toddler_art
            lower.contains("yoga") || lower.contains("active") -> return R.drawable.topic_toddler_dance
            lower.contains("learn") || lower.contains("abc") -> return R.drawable.topic_toddler_abcs
            lower.contains("music") || lower.contains("rhyme") -> return R.drawable.topic_toddler_music
            lower.contains("stor") || lower.contains("read") -> return R.drawable.topic_toddler_stories
        }
    }
    val preset = when {
        lower.contains("animal") || lower.contains("nature") -> "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&h=400&fit=crop"
        lower.contains("cartoon") || lower.contains("fun") -> "https://images.unsplash.com/photo-1587654780291-39c9404d746b?w=600&h=400&fit=crop"
        lower.contains("craft") || lower.contains("art") -> "https://images.unsplash.com/photo-1513364776144-60967b0f800f?w=600&h=400&fit=crop"
        lower.contains("discovery") || lower.contains("world") -> "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&h=400&fit=crop"
        lower.contains("yoga") || lower.contains("active") -> "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=600&h=400&fit=crop"
        lower.contains("learn") || lower.contains("abc") -> "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=600&h=400&fit=crop"
        lower.contains("music") || lower.contains("rhyme") -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&h=400&fit=crop"
        lower.contains("science") || lower.contains("space") -> "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=600&h=400&fit=crop"
        lower.contains("math") || lower.contains("coding") -> "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=600&h=400&fit=crop"
        lower.contains("stor") || lower.contains("read") -> "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=600&h=400&fit=crop"
        lower.contains("game") || lower.contains("gaming") -> "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600&h=400&fit=crop"
        lower.contains("hobb") || lower.contains("diy") -> "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600&h=400&fit=crop"
        lower.contains("sport") || lower.contains("outdoor") -> "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?w=600&h=400&fit=crop"
        lower.contains("cook") || lower.contains("bak") -> "https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=600&h=400&fit=crop"
        else -> null
    }
    return preset
        ?: allVideos.firstOrNull { it.category.equals(name, ignoreCase = true) }?.thumbnailUrl
        ?: "https://images.unsplash.com/photo-1579202673506-ca3ce28943ef?w=600&h=400&fit=crop"
}

private val TODDLER_TOPIC_NAMES = setOf(
    "cartoons & fun",
    "learning & abcs",
    "music & rhymes",
    "animals & nature",
    "art & crafts",
    "stories & reading",
    "kids yoga & active"
)

private fun isToddlerAppropriateCategory(name: String): Boolean {
    return name.trim().lowercase() in TODDLER_TOPIC_NAMES
}

private fun displayTopicLabel(name: String, isToddler: Boolean): String {
    if (!isToddler) return name
    return when (name.trim().lowercase()) {
        "music & rhymes" -> "Songs & Rhymes"
        "animals & nature" -> "Cute Animals"
        "cartoons & fun" -> "Cartoons & Fun"
        "learning & abcs" -> "ABCs & Colors"
        "stories & reading" -> "Bedtime Stories"
        "art & crafts" -> "Colors & Play"
        "kids yoga & active" -> "Sing & Dance"
        else -> name
    }
}

private fun topicEmojiForCategory(name: String, isToddler: Boolean = false): String {
    val lower = name.lowercase()
    if (isToddler) {
        return when {
            lower.contains("animal") || lower.contains("nature") -> "🐶"
            lower.contains("cartoon") || lower.contains("fun") -> "🧸"
            lower.contains("craft") || lower.contains("art") -> "🎨"
            lower.contains("yoga") || lower.contains("active") -> "🤸"
            lower.contains("learn") || lower.contains("abc") -> "🔤"
            lower.contains("music") || lower.contains("rhyme") -> "🎵"
            lower.contains("stor") || lower.contains("read") -> "📖"
            else -> "🎬"
        }
    }
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
        lower.contains("game") || lower.contains("gaming") -> "🎮"
        lower.contains("hobb") || lower.contains("diy") -> "🛠️"
        lower.contains("sport") || lower.contains("outdoor") -> "⚽"
        lower.contains("cook") || lower.contains("bak") -> "🧁"
        else -> "🎬"
    }
}
