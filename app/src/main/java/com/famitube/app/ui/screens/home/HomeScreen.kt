package com.famitube.app.ui.screens.home

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.data.model.Category
import com.famitube.app.data.model.Video
import com.famitube.app.ui.components.CategoryChip
import com.famitube.app.ui.components.FamiTubeTopBar
import com.famitube.app.ui.components.HomeNavTab
import com.famitube.app.ui.components.ParentalGateDialog
import com.famitube.app.ui.components.VideoCard
import com.famitube.app.ui.components.YouTubeStyleTabBar
import com.famitube.app.ui.screens.channels.ChannelsScreen
import com.famitube.app.ui.navigation.Screen
import com.famitube.app.ui.theme.*

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
    onChannelClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val starBalance by (viewModel.starBalance?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val ageFilter by viewModel.ageFilterFlow.collectAsState()
    val favoriteIds by viewModel.favoriteIdsFlow.collectAsState()
    val isSimpleMode = ageFilter == "TODDLER" || ageFilter == "YOUNG_KIDS"
    var selectedNavTab by remember { mutableStateOf(HomeNavTab.HOME) }
    var selectedVideosCategory by remember { mutableStateOf<String?>(null) }

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
                    showSearchButton = !isSimpleMode,
                    unreadNotifications = unreadNotifications,
                    onNotificationsClick = onNavigateToNotifications,
                    showNotificationsButton = true
                )
                YouTubeStyleTabBar(
                    selectedTab = selectedNavTab,
                    onTabSelected = { selectedNavTab = it }
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
                        if (isSimpleMode) {
                            SimpleKidHomeContent(
                                state = state,
                                isToddler = ageFilter == "TODDLER",
                                onSelectCategory = { viewModel.selectCategory(it) },
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick
                            )
                        } else {
                            IntentionalHomeContent(
                                state = state,
                                viewModel = viewModel,
                                favoriteIds = favoriteIds,
                                onSearchClick = onSearchClick,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick
                            )
                        }
                    }
                    HomeNavTab.VIDEOS -> {
                        VideosTabContent(
                            videos = state.allVideos,
                            categories = state.categories,
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
                    HomeNavTab.GAMES -> {
                        KidsGamesContent(
                            starBalance = starBalance,
                            onAwardStars = { amt, desc -> viewModel.awardGameStars(amt, desc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntentionalHomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    favoriteIds: Set<String>,
    onSearchClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit
) {
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
                    text = "Search for a topic, pick a channel, or explore your library.",
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

        // 2. Deliberate Topic Picker
        if (state.categories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
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

        // 3. Deliberate Content
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
                    isFavorite = video.id in favoriteIds,
                    onChannelClick = onChannelClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun VideosTabContent(
    videos: List<Video>,
    categories: List<Category>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    favoriteIds: Set<String>,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val filtered = remember(videos, selectedCategory) {
        if (selectedCategory == null) videos
        else videos.filter { it.category == selectedCategory }
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
                        name = cat.name,
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
                    text = if (selectedCategory == null) "Curated Videos (${filtered.size})" else "$selectedCategory (${filtered.size})",
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

@Composable
private fun KidsGamesContent(
    starBalance: Int,
    onAwardStars: (Int, String) -> Unit
) {
    var activeGameTab by remember { mutableIntStateOf(0) } // 0 = Trivia, 1 = Math, 2 = Memory, 3 = Word

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Star Reward Header Banner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Brain Quests & Games 🎮",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Solve quizzes to earn ⭐ stars for video requests!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2408))
                            .border(1.dp, Color(0xFFFFD166), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⭐ $starBalance",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD166),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Game selector tabs
        item {
            val games = listOf("🧠 Trivia", "⚡ Math Sprint", "🃏 Memory Match", "🔤 Word Quest")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(games) { idx, title ->
                    val isSelected = activeGameTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) PinkPrimary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { activeGameTab = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Active Game Content
        item {
            when (activeGameTab) {
                0 -> TriviaQuestGame(onAwardStars = onAwardStars)
                1 -> MathSprintGame(onAwardStars = onAwardStars)
                2 -> MemoryMatchGame(onAwardStars = onAwardStars)
                else -> WordQuestGame(onAwardStars = onAwardStars)
            }
        }
    }
}

data class TriviaQ(
    val question: String,
    val options: List<String>,
    val answer: String,
    val fact: String
)

@Composable
private fun TriviaQuestGame(
    onAwardStars: (Int, String) -> Unit
) {
    val questions = remember {
        listOf(
            TriviaQ("Which planet has giant colorful rings around it?", listOf("Mars", "Saturn", "Jupiter", "Venus"), "Saturn", "Saturn's rings are made of billions of pieces of ice and rock!"),
            TriviaQ("What is the fastest land animal in the world?", listOf("Lion", "Horse", "Cheetah", "Kangaroo"), "Cheetah", "Cheetahs can run up to 70 miles per hour!"),
            TriviaQ("What sweet food do bees produce?", listOf("Chocolate", "Honey", "Jam", "Juice"), "Honey", "A single bee makes about 1/12th of a teaspoon of honey in its lifetime!"),
            TriviaQ("Which is the largest ocean on Earth?", listOf("Atlantic", "Pacific", "Indian", "Arctic"), "Pacific", "The Pacific Ocean is bigger than all the Earth's continents combined!"),
            TriviaQ("How many colors are in a standard rainbow?", listOf("5", "6", "7", "8"), "7", "Red, Orange, Yellow, Green, Blue, Indigo, and Violet!"),
            TriviaQ("What do plants use to make food from sunlight?", listOf("Photosynthesis", "Digestion", "Hibernation", "Evaporation"), "Photosynthesis", "Leaves absorb sunlight and carbon dioxide to make sugar!")
        )
    }

    var qIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }

    val q = questions[qIndex % questions.size]

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Question ${(qIndex % questions.size) + 1} of ${questions.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BlueSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = q.question,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            q.options.forEach { opt ->
                val isCorrect = opt == q.answer
                val isSelected = selectedOption == opt
                val btnColor = when {
                    !hasAnswered -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrect -> Color(0xFF06D6A0)
                    isSelected -> Color(0xFFEF476F)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(btnColor)
                        .clickable(enabled = !hasAnswered) {
                            selectedOption = opt
                            hasAnswered = true
                            if (isCorrect) {
                                onAwardStars(10, "Trivia Quest: ${q.question}")
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = opt,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected || (hasAnswered && isCorrect)) FontWeight.Bold else FontWeight.Medium,
                        color = if (hasAnswered && (isCorrect || isSelected)) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (hasAnswered) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCorrect = selectedOption == q.answer
                Text(
                    text = if (isCorrect) "🎉 Correct! +10 ⭐ Stars added!" else "Almost! The answer was ${q.answer}.",
                    color = if (isCorrect) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Did you know? ${q.fact}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        hasAnswered = false
                        selectedOption = null
                        qIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Question →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MathSprintGame(
    onAwardStars: (Int, String) -> Unit
) {
    var round by remember { mutableIntStateOf(1) }
    var a by remember { mutableIntStateOf(6) }
    var b by remember { mutableIntStateOf(7) }
    var selectedAns by remember { mutableStateOf<Int?>(null) }
    var answered by remember { mutableStateOf(false) }

    val correct = a + b
    val options = remember(a, b) {
        listOf(correct, correct - 2, correct + 3, correct + 1).shuffled()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "⚡ Math Sprint • Round $round",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD166)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$a + $b = ?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { opt ->
                    val isCorr = opt == correct
                    val isSel = selectedAns == opt
                    val bg = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant
                        isCorr -> Color(0xFF06D6A0)
                        isSel -> Color(0xFFEF476F)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable(enabled = !answered) {
                                selectedAns = opt
                                answered = true
                                if (isCorr) {
                                    onAwardStars(10, "Math Sprint: $a + $b")
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$opt",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (answered && (isCorr || isSel)) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCorr = selectedAns == correct
                Text(
                    text = if (isCorr) "⭐ Brilliant! +10 Stars added!" else "Keep practicing! $a + $b is $correct.",
                    color = if (isCorr) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedAns = null
                        round++
                        a = (3..12).random()
                        b = (4..15).random()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Math Puzzle →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MemoryMatchGame(
    onAwardStars: (Int, String) -> Unit
) {
    val items = remember { listOf("🚀", "🚀", "🦁", "🦁", "🐬", "🐬").shuffled() }
    var flipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var matched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var firstPick by remember { mutableStateOf<Int?>(null) }
    var isAwarded by remember { mutableStateOf(false) }

    LaunchedEffect(matched.size) {
        if (matched.size == 6 && !isAwarded) {
            isAwarded = true
            onAwardStars(20, "Memory Match Champion")
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🃏 Memory Match • Find 3 Pairs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                Text(
                    text = "Matched: ${matched.size / 2}/3",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            val isFlipped = idx in flipped || idx in matched
                            val isMatch = idx in matched

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isMatch -> Color(0xFF06D6A0).copy(alpha = 0.35f)
                                            isFlipped -> Color(0xFF202636)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isMatch) Color(0xFF06D6A0) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isFlipped && firstPick != idx) {
                                        if (firstPick == null) {
                                            firstPick = idx
                                            flipped = setOf(idx)
                                        } else {
                                            val prev = firstPick!!
                                            flipped = setOf(prev, idx)
                                            firstPick = null
                                            if (items[prev] == items[idx]) {
                                                matched = matched + setOf(prev, idx)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFlipped) items[idx] else "❓",
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }

            if (matched.size == 6) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "🎉 Awesome! You matched all pairs! +20 ⭐ Stars awarded!",
                    color = Color(0xFF06D6A0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun WordQuestGame(
    onAwardStars: (Int, String) -> Unit
) {
    val words = remember {
        listOf(
            Triple("P L A N E T", "A celestial body in orbit", listOf("PLANET", "PLANT", "PLATE", "PLANE")),
            Triple("R O B O T", "A machine that helps humans", listOf("ROBOT", "ROOT", "BOOT", "BOAT")),
            Triple("O C E A N", "Vast body of salt water", listOf("OCEAN", "ONION", "OASIS", "OPEN")),
            Triple("S P A C E", "Everything beyond Earth", listOf("SPACE", "SPICE", "SAUCE", "SPECS"))
        )
    }

    var wIdx by remember { mutableIntStateOf(0) }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }

    val current = words[wIdx % words.size]
    val correct = current.third.first()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🔤 Word Quest • Unscramble",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PinkPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = current.first,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4CC9F0),
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clue: ${current.second}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            current.third.shuffled(java.util.Random(current.first.hashCode().toLong())).forEach { opt ->
                val isCorr = opt == correct
                val isSel = selectedWord == opt
                val bg = when {
                    !answered -> MaterialTheme.colorScheme.surfaceVariant
                    isCorr -> Color(0xFF06D6A0)
                    isSel -> Color(0xFFEF476F)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable(enabled = !answered) {
                            selectedWord = opt
                            answered = true
                            if (isCorr) {
                                onAwardStars(10, "Word Quest: $correct")
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = opt,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (answered && (isCorr || isSel)) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCorr = selectedWord == correct
                Text(
                    text = if (isCorr) "⭐ Correct! +10 Stars added!" else "Good try! The word is $correct.",
                    color = if (isCorr) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedWord = null
                        wIdx++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Word →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
}

@Composable
private fun SimpleKidHomeContent(
    state: HomeUiState,
    isToddler: Boolean,
    onSelectCategory: (String) -> Unit,
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
                        onClick = { onVideoClick(video.id) },
                        isFavorite = video.id in favoriteIds,
                        onChannelClick = onChannelClick
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
        lower.contains("game") || lower.contains("gaming") -> "🎮"
        lower.contains("hobb") || lower.contains("diy") -> "🛠️"
        lower.contains("sport") || lower.contains("outdoor") -> "⚽"
        lower.contains("cook") || lower.contains("bak") -> "🧁"
        else -> "🎬"
    }
}
