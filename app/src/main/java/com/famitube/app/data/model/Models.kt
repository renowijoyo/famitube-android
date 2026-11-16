package com.famitube.app.data.model

import com.google.gson.annotations.SerializedName

enum class ChannelType {
    @SerializedName("ON_DEMAND")
    ON_DEMAND,

    @SerializedName("LIVE_TV")
    LIVE_TV
}

data class Channel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("avatarUrl") val avatarUrl: String,
    @SerializedName("bannerUrl") val bannerUrl: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("isVerified") val isVerified: Boolean = true,
    @SerializedName("channelType") val channelType: ChannelType = ChannelType.ON_DEMAND,
    @SerializedName("videoCount") val videoCount: Int = 0
)

data class UpNextItem(
    @SerializedName("video") val video: Video,
    @SerializedName("startsInSeconds") val startsInSeconds: Int
)

data class LiveChannelState(
    @SerializedName("channel") val channel: Channel,
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("currentVideo") val currentVideo: Video? = null,
    @SerializedName("offsetSeconds") val offsetSeconds: Int = 0,
    @SerializedName("remainingSeconds") val remainingSeconds: Int = 0,
    @SerializedName("upNext") val upNext: List<UpNextItem> = emptyList()
)

data class ChannelDetailResponse(
    @SerializedName("channel") val channel: Channel,
    @SerializedName("videos") val videos: List<Video> = emptyList(),
    @SerializedName("liveState") val liveState: LiveChannelState? = null
)

data class Category(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String,
    @SerializedName("color") val color: String,
    @SerializedName("videoCount") val videoCount: Int = 0
)

data class Playlist(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("videoIds") val videoIds: List<String> = emptyList(),
    @SerializedName("category") val category: String = "",
    @SerializedName("ageRating") val ageRating: String = "ALL"
)

data class ParentalDefaults(
    @SerializedName("defaultWatchLimitMinutes") val defaultWatchLimitMinutes: Int = 45,
    @SerializedName("enforcePin") val enforcePin: Boolean = true
)

data class AppConfig(
    @SerializedName("appName") val appName: String,
    @SerializedName("version") val version: String,
    @SerializedName("welcomeMessage") val welcomeMessage: String,
    @SerializedName("parentalDefaults") val parentalDefaults: ParentalDefaults
)

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("loginType") val loginType: String = "CHILD"
)

data class AuthUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String = "CHILD",
    @SerializedName("parentId") val parentId: String? = null,
    @SerializedName("ageRating") val ageRating: String? = null
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: AuthUser? = null,
    @SerializedName("message") val message: String? = null
)

data class KidsFeedData(
    @SerializedName("channels") val channels: List<Channel> = emptyList(),
    @SerializedName("videos") val videos: List<Video> = emptyList()
)

// ==========================================
// CHANNEL QUIZ WALL
// ==========================================

data class QuizQuestion(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String = "MATH",
    @SerializedName("prompt") val prompt: String,
    @SerializedName("options") val options: List<String> = emptyList()
)

data class ChannelAccessData(
    @SerializedName("unlocked") val unlocked: Boolean = true,
    @SerializedName("quizRequired") val quizRequired: Boolean? = null,
    @SerializedName("status") val status: String? = null, // QUIZ_REQUIRED, COOLDOWN, LOCKED
    @SerializedName("quizType") val quizType: String? = null,
    @SerializedName("wrongAttempts") val wrongAttempts: Int = 0,
    @SerializedName("maxAttempts") val maxAttempts: Int = 3,
    @SerializedName("retryWaitSeconds") val retryWaitSeconds: Int = 0,
    @SerializedName("lockoutAction") val lockoutAction: String? = null,
    @SerializedName("lockoutDurationSeconds") val lockoutDurationSeconds: Int = 0,
    @SerializedName("remainingSeconds") val remainingSeconds: Int = 0,
    @SerializedName("unlockedUntil") val unlockedUntil: String? = null,
    @SerializedName("nextQuizAt") val nextQuizAt: String? = null,
    @SerializedName("lockedUntil") val lockedUntil: String? = null,
    @SerializedName("grantedAfterWait") val grantedAfterWait: Boolean = false,
    @SerializedName("question") val question: QuizQuestion? = null
)

data class QuizAnswerRequest(
    @SerializedName("questionId") val questionId: String,
    @SerializedName("selectedIndex") val selectedIndex: Int
)

data class QuizAnswerData(
    @SerializedName("correct") val correct: Boolean = false,
    @SerializedName("unlocked") val unlocked: Boolean = false,
    @SerializedName("status") val status: String? = null,
    @SerializedName("lockoutAction") val lockoutAction: String? = null,
    @SerializedName("lockedUntil") val lockedUntil: String? = null,
    @SerializedName("nextQuizAt") val nextQuizAt: String? = null,
    @SerializedName("remainingSeconds") val remainingSeconds: Int = 0,
    @SerializedName("wrongAttempts") val wrongAttempts: Int = 0,
    @SerializedName("maxAttempts") val maxAttempts: Int = 3,
    @SerializedName("unlockedUntil") val unlockedUntil: String? = null,
    @SerializedName("message") val message: String? = null
)

// ==========================================
// KID YOUTUBE SEARCH, HISTORY & PLAYLISTS
// ==========================================

data class SearchSettings(
    @SerializedName("youtubeSearchEnabled") val youtubeSearchEnabled: Boolean = true,
    @SerializedName("searchViewLimit") val searchViewLimit: Int = 0,
    @SerializedName("searchViewsUsed") val searchViewsUsed: Int = 0,
    @SerializedName("searchRequireApproval") val searchRequireApproval: Boolean = true
)

data class YouTubeSearchResult(
    @SerializedName("youtubeVideoId") val youtubeVideoId: String,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String = "YouTube",
    @SerializedName("thumbnailUrl") val thumbnailUrl: String = "",
    @SerializedName("durationSeconds") val durationSeconds: Int? = null,
    @SerializedName("sourceType") val sourceType: String = "YOUTUBE",
    @SerializedName("sourceUrl") val sourceUrl: String = "",
    @SerializedName("isCurated") val isCurated: Boolean = false,
    @SerializedName("curatedVideoId") val curatedVideoId: String? = null,
    @SerializedName("approvalStatus") val approvalStatus: String? = null,
    @SerializedName("inHistory") val inHistory: Boolean = false
)

data class YouTubeSearchData(
    @SerializedName("results") val results: List<YouTubeSearchResult> = emptyList(),
    @SerializedName("searchSettings") val searchSettings: SearchSettings = SearchSettings()
)

data class YouTubeWatchRequest(
    @SerializedName("youtubeVideoId") val youtubeVideoId: String,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String
)

data class YouTubeWatchData(
    @SerializedName("allowed") val allowed: Boolean = false,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("searchSettings") val searchSettings: SearchSettings? = null
)

data class KidHistoryItem(
    @SerializedName("id") val id: String,
    @SerializedName("itemKey") val itemKey: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String = "FamiTube",
    @SerializedName("thumbnailUrl") val thumbnailUrl: String = "",
    @SerializedName("sourceType") val sourceType: String = "YOUTUBE",
    @SerializedName("sourceUrl") val sourceUrl: String = "",
    @SerializedName("isApprovedSearch") val isApprovedSearch: Boolean = false,
    @SerializedName("watchedAt") val watchedAt: String = ""
)

data class KidHistoryPostRequest(
    @SerializedName("itemKey") val itemKey: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("sourceType") val sourceType: String = "YOUTUBE",
    @SerializedName("sourceUrl") val sourceUrl: String
)

data class KidPlaylistItem(
    @SerializedName("key") val key: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String = "YouTube",
    @SerializedName("thumbnailUrl") val thumbnailUrl: String = "",
    @SerializedName("sourceType") val sourceType: String = "YOUTUBE",
    @SerializedName("sourceUrl") val sourceUrl: String = "",
    @SerializedName("addedAt") val addedAt: String = ""
)

data class KidPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("kidId") val kidId: String = "",
    @SerializedName("title") val title: String,
    @SerializedName("items") val items: List<KidPlaylistItem> = emptyList(),
    @SerializedName("createdAt") val createdAt: String = ""
)

data class CreateKidPlaylistRequest(
    @SerializedName("title") val title: String
)

data class AddKidPlaylistItemRequest(
    @SerializedName("key") val key: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("sourceType") val sourceType: String = "YOUTUBE",
    @SerializedName("sourceUrl") val sourceUrl: String
)
