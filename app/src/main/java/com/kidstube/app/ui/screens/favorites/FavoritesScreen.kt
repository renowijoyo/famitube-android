package com.kidstube.app.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidstube.app.data.model.Video
import com.kidstube.app.data.repository.KidsTubeRepository
import com.kidstube.app.ui.components.VideoCard
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    repository: KidsTubeRepository,
    onVideoClick: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var favoriteVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var historyVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            val favRes = repository.getFavoriteVideos()
            val histRes = repository.getHistoryVideos()
            favoriteVideos = favRes.getOrDefault(emptyList())
            historyVideos = histRes.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Library", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceDark,
                contentColor = PinkPrimary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Favorites (${favoriteVideos.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Watch History (${historyVideos.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PinkPrimary
                    )
                } else {
                    val currentList = if (selectedTabIndex == 0) favoriteVideos else historyVideos
                    if (currentList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (selectedTabIndex == 0) "No favorites saved yet" else "No watch history yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selectedTabIndex == 0) "Tap the heart on any video to keep it here!" else "Watch some videos to see them here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(currentList) { video ->
                                VideoCard(video = video, onClick = { onVideoClick(video.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
