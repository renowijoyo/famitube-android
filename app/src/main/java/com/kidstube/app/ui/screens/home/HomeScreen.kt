package com.kidstube.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kidstube.app.data.model.Video
import com.kidstube.app.ui.components.CategoryChip
import com.kidstube.app.ui.components.KidsTubeTopBar
import com.kidstube.app.ui.components.ParentalGateDialog
import com.kidstube.app.ui.components.VideoCard
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToParental: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showParentalGate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.updateRemainingTime()
    }

    Scaffold(
        topBar = {
            KidsTubeTopBar(
                remainingMinutes = state.remainingMinutes,
                onSearchClick = onSearchClick,
                onParentalClick = { showParentalGate = true }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PinkPrimary
                )
            } else if (state.errorMessage != null && state.videos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Cannot connect to KidsTube Server",
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Category Chips Slider
                    if (state.categories.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.categories) { category ->
                                    CategoryChip(
                                        name = category.name,
                                        isSelected = state.selectedCategory == category.name ||
                                                (state.selectedCategory == null && category.name == "All Videos"),
                                        onClick = { viewModel.selectCategory(category.name) }
                                    )
                                }
                            }
                        }
                    }

                    // Featured Spotlight Card
                    state.featuredVideo?.let { featured ->
                        item {
                            FeaturedVideoCard(
                                video = featured,
                                onClick = { onVideoClick(featured.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Videos Section Header
                    item {
                        Text(
                            text = state.selectedCategory ?: "Recommended for You",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
                        )
                    }

                    // Videos List
                    items(state.videos) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Parental PIN Gate Dialog
            if (showParentalGate) {
                ParentalGateDialog(
                    onDismiss = { showParentalGate = false },
                    onSuccess = {
                        showParentalGate = false
                        onNavigateToParental()
                    },
                    verifyPin = { pin -> viewModel.verifyParentPin(pin) }
                )
            }
        }
    }
}

@Composable
fun FeaturedVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xEE0F172A)),
                            startY = 60f
                        )
                    )
            )

            // Featured Tag
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PinkPrimary)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "FEATURED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // Title and Channel on gradient
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.channelTitle} â€¢ ${video.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CC9F0)
                )
            }
        }
    }
}
