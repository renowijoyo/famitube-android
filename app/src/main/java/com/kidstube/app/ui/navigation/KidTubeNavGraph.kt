package com.kidstube.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kidstube.app.data.model.ChannelType
import com.kidstube.app.data.repository.KidsTubeRepository
import com.kidstube.app.ui.components.KidsTubeBottomBar
import com.kidstube.app.ui.screens.channels.ChannelDetailScreen
import com.kidstube.app.ui.screens.channels.ChannelsScreen
import com.kidstube.app.ui.screens.favorites.FavoritesScreen
import com.kidstube.app.ui.screens.home.HomeScreen
import com.kidstube.app.ui.screens.home.HomeViewModel
import com.kidstube.app.ui.screens.login.LoginScreen
import com.kidstube.app.ui.screens.parental.ParentalSettingsScreen
import com.kidstube.app.ui.screens.player.LiveTvPlayerScreen
import com.kidstube.app.ui.screens.player.PlayerScreen
import com.kidstube.app.ui.screens.player.PlayerViewModel
import com.kidstube.app.ui.screens.playlists.PlaylistsScreen
import com.kidstube.app.ui.screens.search.SearchScreen

@Composable
fun KidsTubeNavGraph(
    navController: NavHostController,
    repository: KidsTubeRepository,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on primary top-level tabs
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Channels.route,
        Screen.Playlists.route,
        Screen.Favorites.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                KidsTubeBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (repository.isLoggedIn()) Screen.Home.route else Screen.Login.route,
            modifier = modifier.padding(innerPadding)
        ) {
            // Child Login Gate (only parent-registered child email/Gmail allowed)
            composable(Screen.Login.route) {
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home / Explore Tab
            composable(Screen.Home.route) {
                val homeViewModel = rememberHomeViewModel(repository)
                HomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onNavigateToParental = {
                        navController.navigate(Screen.ParentalSettings.route)
                    }
                )
            }

            // Channels Tab
            composable(Screen.Channels.route) {
                ChannelsScreen(
                    repository = repository,
                    onChannelClick = { channel ->
                        if (channel.channelType == ChannelType.LIVE_TV) {
                            navController.navigate(Screen.LiveTvPlayer.createRoute(channel.id))
                        } else {
                            navController.navigate(Screen.ChannelDetail.createRoute(channel.id))
                        }
                    }
                )
            }

            // YouTube-style On-Demand Channel Detail
            composable(
                route = Screen.ChannelDetail.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { backStack ->
                val channelId = backStack.arguments?.getString("channelId") ?: ""
                ChannelDetailScreen(
                    repository = repository,
                    channelId = channelId,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    }
                )
            }

            // Live TV Scheduled Channel Player (Runtime Locked)
            composable(
                route = Screen.LiveTvPlayer.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { backStack ->
                val channelId = backStack.arguments?.getString("channelId") ?: ""
                LiveTvPlayerScreen(
                    repository = repository,
                    channelId = channelId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Playlists Tab
            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    repository = repository,
                    onPlaylistClick = { playlist ->
                        if (playlist.videoIds.isNotEmpty()) {
                            navController.navigate(Screen.Player.createRoute(playlist.videoIds.first()))
                        }
                    }
                )
            }

            // Favorites Tab
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    repository = repository,
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    }
                )
            }

            // Safe Search Screen
            composable(Screen.Search.route) {
                SearchScreen(
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    }
                )
            }

            // Video Player Screen
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStack ->
                val videoId = backStack.arguments?.getString("videoId") ?: ""
                val playerViewModel = rememberPlayerViewModel(repository, videoId)
                PlayerScreen(
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() },
                    onRelatedVideoClick = { relatedId ->
                        navController.navigate(Screen.Player.createRoute(relatedId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // Parental Controls Screen
            composable(Screen.ParentalSettings.route) {
                ParentalSettingsScreen(
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onLogout = {
                        repository.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun rememberHomeViewModel(repository: KidsTubeRepository): HomeViewModel {
    return androidx.lifecycle.viewmodel.compose.viewModel {
        HomeViewModel(repository)
    }
}

@Composable
fun rememberPlayerViewModel(repository: KidsTubeRepository, videoId: String): PlayerViewModel {
    return androidx.lifecycle.viewmodel.compose.viewModel(key = videoId) {
        PlayerViewModel(repository, videoId)
    }
}
