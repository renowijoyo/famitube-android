package com.famitube.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.ui.theme.PinkLight
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark
import com.famitube.app.ui.theme.SurfaceLightDark
import com.famitube.app.ui.theme.YellowWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiTubeTopBar(
    remainingMinutes: Int,
    onSearchClick: () -> Unit,
    starBalance: Int? = null,
    onStarsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    showSearchButton: Boolean = true,
    onBrowserClick: () -> Unit = {},
    showBrowserButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Playful Logo Box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PinkPrimary, PinkLight)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "F",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "FamiTube",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            if (starBalance != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(YellowWarning.copy(alpha = 0.2f))
                        .clickable(onClick = onStarsClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "⭐ $starBalance",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Remaining Watch Time Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                val timeText = if (remainingMinutes < 0) "No limit" else "${remainingMinutes}m left"
                val timeColor = when {
                    remainingMinutes < 0 -> Color(0xFF059669)
                    remainingMinutes < 10 -> Color(0xFFDC2626)
                    else -> Color(0xFF0284C7)
                }
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = timeColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            if (showBrowserButton) {
                IconButton(onClick = onBrowserClick) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Safe Browser",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (showSearchButton) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Safe Videos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "My Profile",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}
