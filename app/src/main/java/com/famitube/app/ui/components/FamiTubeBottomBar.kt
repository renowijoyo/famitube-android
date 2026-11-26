package com.famitube.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
    isParentMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val items = if (isParentMode) {
            listOf(
                Triple(Screen.ParentDashboard.route, "Dashboard", Icons.Default.FamilyRestroom),
                Triple(Screen.Notifications.route, "Alerts", Icons.Default.Notifications),
                Triple(Screen.ParentalSettings.route, "Settings", Icons.Default.Settings)
            )
        } else if (isSimpleMode) {
            listOf(
                Triple(Screen.Home.route, "TV", Icons.Default.Tv),
                Triple(Screen.Favorites.route, "Favorites", Icons.Default.Favorite),
                Triple(Screen.Profile.route, "Profile", Icons.Default.Person)
            )
        } else {
            listOf(
                Triple(Screen.Home.route, "Explore", Icons.Default.Home),
                Triple(Screen.Channels.route, "Channels", Icons.Default.Tv),
                Triple(Screen.Library.route, "Library", Icons.Default.VideoLibrary),
                Triple(Screen.Profile.route, "Profile", Icons.Default.Person)
            )
        }

        items.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route || (route == Screen.Library.route && currentRoute == "playlists")
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
