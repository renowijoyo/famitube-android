package com.kidstube.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kidstube.app.ui.navigation.Screen
import com.kidstube.app.ui.theme.PinkPrimary
import com.kidstube.app.ui.theme.SurfaceDark

@Composable
fun KidsTubeBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = SurfaceDark,
        contentColor = Color.White
    ) {
        val items = listOf(
            Triple(Screen.Home.route, "Explore", Icons.Default.Home),
            Triple(Screen.Channels.route, "Channels", Icons.Default.Tv),
            Triple(Screen.Playlists.route, "Playlists", Icons.Default.VideoLibrary),
            Triple(Screen.Favorites.route, "Favorites", Icons.Default.Favorite)
        )

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
