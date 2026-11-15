package com.famitube.app.ui.screens.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.data.model.AchievementCategory
import com.famitube.app.data.model.AchievementProgress
import com.famitube.app.data.model.GamificationSummary
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.CategoryChip
import com.famitube.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    repository: FamiTubeRepository
) {
    val achievementManager = repository.getAchievementManager()
    val summary by (achievementManager?.summaryFlow?.collectAsState()
        ?: remember {
            mutableStateOf(
                GamificationSummary(
                    totalXp = 0,
                    level = 1,
                    rankTitle = "Curious Explorer",
                    xpInCurrentLevel = 0,
                    xpForNextLevel = 100,
                    unlockedCount = 0,
                    totalCount = 0,
                    currentStreakDays = 0,
                    achievements = emptyList()
                )
            )
        })

    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }

    val filteredAchievements = remember(summary.achievements, selectedCategory) {
        val base = if (selectedCategory == null) {
            summary.achievements
        } else {
            summary.achievements.filter { it.achievement.category == selectedCategory }
        }
        base.sortedWith(
            compareByDescending<AchievementProgress> { it.isUnlocked }
                .thenByDescending { it.progressFraction }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = YellowWarning,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("My Badges", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (summary.currentStreakDays > 0) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceLightDark)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "🔥 ${summary.currentStreakDays}d streak",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = YellowWarning
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Level & XP Summary Hero Card
            item {
                LevelProgressHeroCard(
                    summary = summary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Category Filter Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryChip(
                            name = "All (${summary.totalCount})",
                            isSelected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                    }
                    items(AchievementCategory.entries) { category ->
                        CategoryChip(
                            name = category.label,
                            isSelected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Achievement Cards
            items(filteredAchievements, key = { it.achievement.id }) { item ->
                AchievementCard(
                    progress = item,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LevelProgressHeroCard(
    summary: GamificationSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF282047), SurfaceDark)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(PinkPrimary.copy(alpha = 0.6f), BlueSecondary.copy(alpha = 0.6f))),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(PinkPrimary, YellowWarning))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LV",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "${summary.level}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.rankTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${summary.unlockedCount} of ${summary.totalCount} badges unlocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyanAccent.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${summary.totalXp} XP",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = CyanAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Next Level",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = "${summary.xpInCurrentLevel} / ${summary.xpForNextLevel} XP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = YellowWarning
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { summary.levelProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = YellowWarning,
            trackColor = SurfaceLightDark
        )
    }
}

@Composable
private fun AchievementCard(
    progress: AchievementProgress,
    modifier: Modifier = Modifier
) {
    val achievement = progress.achievement
    val isUnlocked = progress.isUnlocked

    val borderBrush = if (isUnlocked) {
        Brush.horizontalGradient(listOf(MintSuccess.copy(alpha = 0.7f), CyanAccent.copy(alpha = 0.6f)))
    } else {
        Brush.horizontalGradient(listOf(BorderDark, BorderDark))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(width = 1.dp, brush = borderBrush, shape = RoundedCornerShape(18.dp))
            .padding(14.dp)
            .alpha(if (isUnlocked) 1f else 0.85f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge Circle
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked) MintSuccess.copy(alpha = 0.18f)
                    else SurfaceLightDark
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = achievement.badgeEmoji,
                fontSize = 26.sp,
                modifier = Modifier.alpha(if (isUnlocked) 1f else 0.45f)
            )
            if (!isUnlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(BackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) Color.White else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isUnlocked) MintSuccess.copy(alpha = 0.2f)
                            else BlueSecondary.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "+${achievement.xpReward} XP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) MintSuccess else Color(0xFF93C5FD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isUnlocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MintSuccess,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Unlocked!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MintSuccess
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PinkPrimary,
                        trackColor = SurfaceLightDark
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${progress.currentProgress}/${achievement.targetProgress}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
