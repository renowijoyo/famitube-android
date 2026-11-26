package com.famitube.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.famitube.app.data.local.NotificationHelper
import com.famitube.app.data.model.Achievement
import com.famitube.app.data.model.ChannelType
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.components.AchievementUnlockedBanner
import com.famitube.app.ui.components.FamiTubeBottomBar
import com.famitube.app.ui.screens.achievements.AchievementsScreen

import com.famitube.app.ui.screens.notifications.NotificationsScreen
import kotlinx.coroutines.delay
import com.famitube.app.ui.screens.channels.ChannelDetailScreen
import com.famitube.app.ui.screens.channels.ChannelsScreen
import com.famitube.app.ui.screens.favorites.FavoritesScreen
import com.famitube.app.ui.screens.home.HomeScreen
import com.famitube.app.ui.screens.home.HomeViewModel
import com.famitube.app.ui.screens.library.LibraryScreen
import com.famitube.app.ui.screens.login.LoginScreen
import com.famitube.app.ui.screens.parental.ParentDashboardScreen
import com.famitube.app.ui.screens.parental.ParentalSettingsScreen
import com.famitube.app.ui.screens.player.LiveTvPlayerScreen
import com.famitube.app.ui.screens.player.PlayerScreen
import com.famitube.app.ui.screens.player.ToddlerTvPlayerScreen
import com.famitube.app.ui.screens.player.PlayerViewModel
import com.famitube.app.ui.screens.playlists.PlaylistsScreen
import com.famitube.app.ui.screens.profile.ProfileScreen
import com.famitube.app.ui.screens.search.SearchScreen
import com.famitube.app.ui.screens.stories.StoryReaderScreen

@Composable
fun FamiTubeNavGraph(
    navController: NavHostController,
    repository: FamiTubeRepository,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Keep bottom bar always visible at all times (except login screen)
    val showBottomBar = currentRoute != null && currentRoute != Screen.Login.route

    var unlockedBanner by remember { mutableStateOf<Achievement?>(null) }
    var unreadNotifications by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val achievementManager = repository.getAchievementManager()
    val ageFilter by repository.getPreferences().ageFilterFlow.collectAsState()
    val isSimpleMode = ageFilter == "TODDLER"

    LaunchedEffect(currentRoute) {
        if (repository.isLoggedIn()) {
            while (true) {
                repository.getNotifications(50).onSuccess { data ->
                    unreadNotifications = data.unreadCount
                    NotificationHelper.showNewNotifications(context, data.items)
                }
                delay(30_000)
            }
        }
    }

    LaunchedEffect(achievementManager) {
        achievementManager?.unlockEvents?.collect { achievement ->
            unlockedBanner = achievement
        }
    }

    LaunchedEffect(unlockedBanner) {
        if (unlockedBanner != null) {
            delay(3500)
            unlockedBanner = null
        }
    }

    LaunchedEffect(isSimpleMode, currentRoute) {
        if (isSimpleMode && (currentRoute == Screen.Library.route || currentRoute == "playlists")) {
            navController.navigate(Screen.Favorites.route) {
                popUpTo(Screen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val isParentMode = currentRoute == Screen.ParentDashboard.route ||
                        currentRoute == Screen.ParentalSettings.route ||
                        (repository.isParentMode() && currentRoute !in listOf(
                            Screen.Home.route,
                            Screen.Channels.route,
                            Screen.Library.route,
                            Screen.Favorites.route,
                            Screen.Player.route,
                            Screen.ChannelDetail.route,
                            Screen.StoryReader.route
                        ))
                FamiTubeBottomBar(
                    currentRoute = currentRoute,
                    isSimpleMode = isSimpleMode,
                    isParentMode = isParentMode,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            val popDestination = if (isParentMode) Screen.ParentDashboard.route else Screen.Home.route
                            popUpTo(popDestination) { saveState = true }
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
            startDestination = when {
                !repository.isLoggedIn() -> Screen.Login.route
                repository.isParentMode() -> Screen.ParentDashboard.route
                else -> Screen.Home.route
            },
            modifier = modifier.padding(innerPadding)
        ) {
            // Child Login Gate (main login screen, with Parent Login toggle)
            composable(Screen.Login.route) {
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {
                        val dest = if (repository.isParentMode()) {
                            Screen.ParentDashboard.route
                        } else {
                            Screen.Home.route
                        }
                        navController.navigate(dest) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Parent Mode Dashboard (shown when signed in with a parent account)
            composable(Screen.ParentDashboard.route) {
                ParentDashboardScreen(
                    repository = repository,
                    unreadNotifications = unreadNotifications,
                    onOpenNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.ParentalSettings.route)
                    },
                    onPreviewChildMode = {
                        navController.navigate(Screen.Home.route)
                    },
                    onLogout = {
                        repository.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
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
                    onChannelClick = { channelId ->
                        navController.navigate(Screen.ChannelDetail.createRoute(channelId))
                    },
                    onToddlerChannelClick = { topic ->
                        navController.navigate(Screen.ToddlerTvPlayer.createRoute(topic))
                    },
                    onStoryClick = { storyId ->
                        navController.navigate(Screen.StoryReader.createRoute(storyId))
                    },
                    onNavigateToBadges = {
                        navController.navigate(Screen.Achievements.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    },
                    unreadNotifications = unreadNotifications,
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateTab = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // Notifications & Notification History
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onUnreadCountChanged = { unreadNotifications = it }
                )
            }

            // Profile & Theme Settings
            composable(Screen.Profile.route) {
                ProfileScreen(
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToFavorites = {
                        navController.navigate(Screen.Favorites.route)
                    },
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    },
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToParental = {
                        navController.navigate(Screen.ParentalSettings.route)
                    },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Channels Tab
            composable(Screen.Channels.route) {
                ChannelsScreen(
                    repository = repository,
                    onChannelClick = { channel ->
                        navController.navigate(Screen.ChannelDetail.createRoute(channel.id))
                    },
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    }
                )
            }

            // YouTube-style Channel Detail & Videos Screen
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
                    },
                    onWatchLive = { liveChannelId ->
                        navController.navigate(Screen.LiveTvPlayer.createRoute(liveChannelId))
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

            composable(
                route = Screen.ToddlerTvPlayer.route,
                arguments = listOf(navArgument("topic") { type = NavType.StringType })
            ) { backStack ->
                val encodedTopic = backStack.arguments?.getString("topic") ?: ""
                val topic = android.net.Uri.decode(encodedTopic)
                ToddlerTvPlayerScreen(
                    repository = repository,
                    topic = topic,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.StoryReader.route,
                arguments = listOf(navArgument("storyId") { type = NavType.StringType })
            ) { backStack ->
                val storyId = backStack.arguments?.getString("storyId") ?: ""
                StoryReaderScreen(
                    repository = repository,
                    storyId = storyId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Library Tab (disabled in Toddler mode -> Favorites)
            composable(Screen.Library.route) {
                if (isSimpleMode) {
                    FavoritesScreen(
                        repository = repository,
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.ChannelDetail.createRoute(channelId))
                        }
                    )
                } else {
                    LibraryScreen(
                        repository = repository,
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        }
                    )
                }
            }
            composable("playlists") {
                if (isSimpleMode) {
                    FavoritesScreen(
                        repository = repository,
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.ChannelDetail.createRoute(channelId))
                        }
                    )
                } else {
                    LibraryScreen(
                        repository = repository,
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        }
                    )
                }
            }

            // Favorites Tab
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    repository = repository,
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Screen.ChannelDetail.createRoute(channelId))
                    }
                )
            }

            // Badges / Achievements Tab
            composable(Screen.Achievements.route) {
                AchievementsScreen(repository = repository)
            }

            // Safe Search Screen
            composable(Screen.Search.route) {
                SearchScreen(
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Screen.ChannelDetail.createRoute(channelId))
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
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Screen.ChannelDetail.createRoute(channelId))
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

        AchievementUnlockedBanner(
            achievement = unlockedBanner,
            onClick = {
                unlockedBanner = null
                navController.navigate(Screen.Achievements.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}

@Composable
fun rememberHomeViewModel(repository: FamiTubeRepository): HomeViewModel {
    return androidx.lifecycle.viewmodel.compose.viewModel {
        HomeViewModel(repository)
    }
}

@Composable
fun rememberPlayerViewModel(repository: FamiTubeRepository, videoId: String): PlayerViewModel {
    return androidx.lifecycle.viewmodel.compose.viewModel(key = videoId) {
        PlayerViewModel(repository, videoId)
    }
}
