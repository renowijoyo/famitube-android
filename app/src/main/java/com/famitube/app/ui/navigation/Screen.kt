package com.famitube.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Channels : Screen("channels")
    object Library : Screen("library")
    object Playlists : Screen("library")
    object Favorites : Screen("favorites")
    object Achievements : Screen("achievements")
    object Profile : Screen("profile")
    object Search : Screen("search")
    object Notifications : Screen("notifications")
    object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
    object ChannelDetail : Screen("channel/{channelId}") {
        fun createRoute(channelId: String) = "channel/$channelId"
    }
    object LiveTvPlayer : Screen("live_tv/{channelId}") {
        fun createRoute(channelId: String) = "live_tv/$channelId"
    }
    object ToddlerTvPlayer : Screen("toddler_tv/{topic}") {
        fun createRoute(topic: String) = "toddler_tv/${android.net.Uri.encode(topic)}"
    }
    object StoryReader : Screen("story_reader/{storyId}") {
        fun createRoute(storyId: String) = "story_reader/$storyId"
    }
    object GamePlayer : Screen("game_player/{gameId}") {
        fun createRoute(gameId: String) = "game_player/$gameId"
    }
    object ParentalGate : Screen("parental_gate")
    object ParentalSettings : Screen("parental_settings")
    object ParentDashboard : Screen("parent_dashboard")
}
