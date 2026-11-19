package com.famitube.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.R
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
    unreadNotifications: Int = 0,
    onNotificationsClick: () -> Unit = {},
    showNotificationsButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_famitube_logo),
                    contentDescription = "FamiTube",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

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
            if (showNotificationsButton) {
                IconButton(onClick = onNotificationsClick) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge {
                                    Text(if (unreadNotifications > 9) "9+" else unreadNotifications.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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
