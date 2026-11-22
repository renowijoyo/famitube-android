package com.famitube.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.famitube.app.ui.navigation.Screen
import com.famitube.app.ui.theme.PinkPrimary
import com.famitube.app.ui.theme.SurfaceDark

@Composable
fun FamiTubeBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    isSimpleMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val items = if (isSimpleMode) {
            listOf(
                Triple(Screen.Home.route, "Topics", Icons.Default.Home),
                Triple(Screen.Favorites.route, "Favorites", Icons.Default.Favorite)
            )
        } else {
            listOf(
                Triple(Screen.Home.route, "Explore", Icons.Default.Home),
                Triple(Screen.Channels.route, "Channels", Icons.Default.Tv),
                Triple(Screen.Playlists.route, "Playlists", Icons.Default.VideoLibrary),
                Triple(Screen.Favorites.route, "Favorites", Icons.Default.Favorite)
            )
        }

        items.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = label,
                        color = if (isSelected) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = PinkPrimary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
