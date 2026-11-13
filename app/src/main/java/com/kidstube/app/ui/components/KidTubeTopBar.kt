package com.kidstube.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.kidstube.app.ui.theme.PinkLight
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark
import com.kidstube.app.ui.theme.SurfaceLightDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsTubeTopBar(
    remainingMinutes: Int,
    onSearchClick: () -> Unit,
    onParentalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceDark
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
                        text = "K",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "KidsTube",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        },
        actions = {
            // Remaining Watch Time Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLightDark)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "â±ï¸ ${remainingMinutes}m",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingMinutes < 10) Color(0xFFFF5252) else Color(0xFF4CC9F0)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Safe Videos",
                    tint = Color.White
                )
            }

            IconButton(onClick = onParentalClick) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Parental Settings",
                    tint = PinkPrimary
                )
            }
        }
    )
}
