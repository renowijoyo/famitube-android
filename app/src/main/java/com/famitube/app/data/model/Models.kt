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
    @SerializedName("ageRating") val ageRating: String = "ALL",
    @SerializedName("isVerified") val isVerified: Boolean = true,
    @SerializedName("channelType") val channelType: ChannelType = ChannelType.ON_DEMAND,
    @SerializedName("videoCount") val videoCount: Int = 0,
    @SerializedName("ownerId") val ownerId: String? = null,
    @SerializedName("ownerName") val ownerName: String? = null,
    @SerializedName("defaultTags") val defaultTags: List<String> = emptyList()
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
    @SerializedName("tags") val tags: List<String> = emptyList(),
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

data class RegisterParentRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String,
    @SerializedName("referralCode") val referralCode: String? = null
)

data class GenericAuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("referralApplied") val referralApplied: Boolean? = null
)

data class PlanEntitlementsDto(
    @SerializedName("plan") val plan: String = "FREE",
    @SerializedName("isPremium") val isPremium: Boolean = false,
    @SerializedName("planExpiresAt") val planExpiresAt: String? = null,
    @SerializedName("maxChildren") val maxChildren: Int? = 1,
    @SerializedName("maxPlaylists") val maxPlaylists: Int? = 1,
    @SerializedName("maxActivePresets") val maxActivePresets: Int? = 1,
    @SerializedName("allowCustomTagRules") val allowCustomTagRules: Boolean = false,
    @SerializedName("allowPrivateChannels") val allowPrivateChannels: Boolean = false,
    @SerializedName("allowAttentionTracking") val allowAttentionTracking: Boolean = false,
    @SerializedName("allowWeeklyInsights") val allowWeeklyInsights: Boolean = false,
    @SerializedName("dailySearchRequestLimit") val dailySearchRequestLimit: Int? = 3
)

data class UpdatePlanRequest(
    @SerializedName("plan") val plan: String,
    @SerializedName("durationDays") val durationDays: Int? = null
)

data class AuthUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String = "CHILD",
    @SerializedName("parentId") val parentId: String? = null,
    @SerializedName("ageRating") val ageRating: String? = null,
    @SerializedName("plan") val plan: String = "FREE",
    @SerializedName("entitlements") val entitlements: PlanEntitlementsDto? = null
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: AuthUser? = null,
    @SerializedName("message") val message: String? = null
)

data class ParentalSettingsDto(
    @SerializedName("parentPin") val parentPin: String = "1234",
    @SerializedName("dailyLimitMinutes") val dailyLimitMinutes: Int = 45,
    @SerializedName("ageRating") val ageRating: String = "ALL",
    @SerializedName("attentionTrackingEnabled") val attentionTrackingEnabled: Boolean = true,
    @SerializedName("attentionPauseSeconds") val attentionPauseSeconds: Int = 30,
    @SerializedName("attentionMinWatchPercent") val attentionMinWatchPercent: Int = 70,
    @SerializedName("contentLanguage") val contentLanguage: String = "all",
    @SerializedName("cultureMode") val cultureMode: String = "GLOBAL",
    @SerializedName("toddlerLoopMinutes") val toddlerLoopMinutes: Int = 15,
    @SerializedName("toddlerCooldownMinutes") val toddlerCooldownMinutes: Int = 30,
    @SerializedName("toddlerSubStage") val toddlerSubStage: String = "AUTO",
    @SerializedName("toddlerEnabledTopics") val toddlerEnabledTopics: List<String> = emptyList(),
    @SerializedName("toddlerHiddenVideoIds") val toddlerHiddenVideoIds: List<String> = emptyList(),
    @SerializedName("toddlerCooldownUntil") val toddlerCooldownUntil: String? = null
)

data class ToddlerTvChannelLoopDto(
    @SerializedName("topic") val topic: String,
    @SerializedName("displayTitle") val displayTitle: String,
    @SerializedName("emoji") val emoji: String = "📺",
    @SerializedName("videos") val videos: List<Video> = emptyList(),
    @SerializedName("weekNumber") val weekNumber: Int = 0
)

data class ToddlerTvSettingsDto(
    @SerializedName("contentLanguage") val contentLanguage: String = "all",
    @SerializedName("cultureMode") val cultureMode: String = "GLOBAL",
    @SerializedName("toddlerLoopMinutes") val toddlerLoopMinutes: Int = 15,
    @SerializedName("toddlerCooldownMinutes") val toddlerCooldownMinutes: Int = 30,
    @SerializedName("toddlerSubStage") val toddlerSubStage: String = "AUTO",
    @SerializedName("toddlerEnabledTopics") val toddlerEnabledTopics: List<String> = emptyList(),
    @SerializedName("toddlerHiddenVideoIds") val toddlerHiddenVideoIds: List<String> = emptyList()
)

data class ToddlerTvDataDto(
    @SerializedName("channels") val channels: List<ToddlerTvChannelLoopDto> = emptyList(),
    @SerializedName("weekNumber") val weekNumber: Int = 0,
    @SerializedName("serverTime") val serverTime: String = "",
    @SerializedName("cooldownUntil") val cooldownUntil: String? = null,
    @SerializedName("settings") val settings: ToddlerTvSettingsDto = ToddlerTvSettingsDto()
)

data class ToddlerCooldownRequest(
    @SerializedName("action") val action: String = "START",
    @SerializedName("pin") val pin: String? = null
)

data class KidsFeedData(
    @SerializedName("channels") val channels: List<Channel> = emptyList(),
    @SerializedName("videos") val videos: List<Video> = emptyList(),
    @SerializedName("entitlements") val entitlements: PlanEntitlementsDto? = null,
    @SerializedName("parentalSettings") val parentalSettings: ParentalSettingsDto? = null
)

// ==========================================
// KID YOUTUBE SEARCH, HISTORY & PLAYLISTS
// ==========================================

data class AuthMeResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("user") val user: AuthUser? = null
)

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

data class YouTubeChannelSearchResult(
    @SerializedName("handle") val handle: String,
    @SerializedName("channelId") val channelId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("avatarUrl") val avatarUrl: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean = false,
    @SerializedName("trustLevel") val trustLevel: String = "SUPERVISED"
)

data class YouTubeChannelPlaylistDto(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("title") val title: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String = "",
    @SerializedName("videoCount") val videoCount: Int = 0
)

data class YouTubeChannelDetailData(
    @SerializedName("channel") val channel: YouTubeChannelSearchResult,
    @SerializedName("videos") val videos: List<YouTubeSearchResult> = emptyList(),
    @SerializedName("playlists") val playlists: List<YouTubeChannelPlaylistDto> = emptyList()
)

data class YouTubePlaylistDetailData(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("title") val title: String,
    @SerializedName("handle") val handle: String? = null,
    @SerializedName("videos") val videos: List<YouTubeSearchResult> = emptyList()
)

data class SubscribeYouTubeChannelRequest(
    @SerializedName("handle") val handle: String,
    @SerializedName("channelId") val channelId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("avatarUrl") val avatarUrl: String
)

data class YouTubeSearchData(
    @SerializedName("results") val results: List<YouTubeSearchResult> = emptyList(),
    @SerializedName("channels") val channels: List<YouTubeChannelSearchResult> = emptyList(),
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

// ==========================================
// NOTIFICATION MODELS
// ==========================================

data class NotificationItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String = "",
    @SerializedName("type") val type: String = "SYSTEM",
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("relatedId") val relatedId: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String = ""
)

data class NotificationsDataDto(
    @SerializedName("items") val items: List<NotificationItemDto> = emptyList(),
    @SerializedName("unreadCount") val unreadCount: Int = 0
)

data class TagDefinitionDto(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("description") val description: String? = null
)

data class TagCategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("multiSelect") val multiSelect: Boolean = true,
    @SerializedName("tags") val tags: List<TagDefinitionDto> = emptyList()
)

data class ParentFilterPresetDto(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("includeAllTags") val includeAllTags: List<String> = emptyList(),
    @SerializedName("includeAnyTags") val includeAnyTags: List<String> = emptyList(),
    @SerializedName("excludeTags") val excludeTags: List<String> = emptyList()
)

data class TagTaxonomyData(
    @SerializedName("categories") val categories: List<TagCategoryDto> = emptyList(),
    @SerializedName("presets") val presets: List<ParentFilterPresetDto> = emptyList()
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

// ==========================================
// PARENT MODE MODELS
// ==========================================

data class ParentChildDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String = "CHILD",
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("ageRating") val ageRating: String = "ALL",
    @SerializedName("youtubeSearchEnabled") val youtubeSearchEnabled: Boolean = true,
    @SerializedName("searchViewLimit") val searchViewLimit: Int = 0,
    @SerializedName("searchViewsUsed") val searchViewsUsed: Int = 0,
    @SerializedName("searchRequireApproval") val searchRequireApproval: Boolean = true,
    @SerializedName("parentPin") val parentPin: String = "1234",
    @SerializedName("dailyLimitMinutes") val dailyLimitMinutes: Int = 45,
    @SerializedName("attentionTrackingEnabled") val attentionTrackingEnabled: Boolean = true,
    @SerializedName("contentLanguage") val contentLanguage: String = "all",
    @SerializedName("cultureMode") val cultureMode: String = "GLOBAL",
    @SerializedName("toddlerLoopMinutes") val toddlerLoopMinutes: Int = 15,
    @SerializedName("toddlerCooldownMinutes") val toddlerCooldownMinutes: Int = 30,
    @SerializedName("toddlerSubStage") val toddlerSubStage: String = "AUTO",
    @SerializedName("toddlerEnabledTopics") val toddlerEnabledTopics: List<String> = emptyList(),
    @SerializedName("toddlerHiddenVideoIds") val toddlerHiddenVideoIds: List<String> = emptyList(),
    @SerializedName("toddlerCooldownUntil") val toddlerCooldownUntil: String? = null,
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("overrides") val overrides: Map<String, Any?> = emptyMap(),
    @SerializedName("createdAt") val createdAt: String = ""
)

data class GlobalParentalControlsDto(
    @SerializedName("parentPin") val parentPin: String = "1234",
    @SerializedName("dailyLimitMinutes") val dailyLimitMinutes: Int = 45,
    @SerializedName("ageRating") val ageRating: String = "ALL",
    @SerializedName("attentionTrackingEnabled") val attentionTrackingEnabled: Boolean = true,
    @SerializedName("attentionPauseSeconds") val attentionPauseSeconds: Int = 30,
    @SerializedName("attentionMinWatchPercent") val attentionMinWatchPercent: Int = 70,
    @SerializedName("youtubeSearchEnabled") val youtubeSearchEnabled: Boolean = true,
    @SerializedName("searchViewLimit") val searchViewLimit: Int = 0,
    @SerializedName("searchRequireApproval") val searchRequireApproval: Boolean = true,
    @SerializedName("contentLanguage") val contentLanguage: String = "all",
    @SerializedName("cultureMode") val cultureMode: String = "GLOBAL",
    @SerializedName("religion") val religion: String? = null
)

data class UpdateChildControlsRequest(
    @SerializedName("overrides") val overrides: Map<String, Any?>,
    @SerializedName("religion") val religion: String? = null
)

data class RegisterChildRequest(
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("ageRating") val ageRating: String = "ALL",
    @SerializedName("religion") val religion: String? = null
)

data class ParentVideoRequestDto(
    @SerializedName("id") val id: String,
    @SerializedName("kidId") val kidId: String = "",
    @SerializedName("kidName") val kidName: String = "",
    @SerializedName("kidEmail") val kidEmail: String = "",
    @SerializedName("youtubeVideoId") val youtubeVideoId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("channelTitle") val channelTitle: String = "",
    @SerializedName("thumbnailUrl") val thumbnailUrl: String = "",
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("createdAt") val createdAt: String = ""
)

data class DecideVideoRequestBody(
    @SerializedName("decision") val decision: String
)

data class KidYouTubeSearchHistoryItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("kidId") val kidId: String = "",
    @SerializedName("query") val query: String,
    @SerializedName("resultsCount") val resultsCount: Int = 0,
    @SerializedName("regionCode") val regionCode: String? = null,
    @SerializedName("searchedAt") val searchedAt: String = ""
)

data class KidActivityHistoryData(
    @SerializedName("searchHistory") val searchHistory: List<KidYouTubeSearchHistoryItemDto> = emptyList(),
    @SerializedName("videoRequests") val videoRequests: List<ParentVideoRequestDto> = emptyList(),
    @SerializedName("watchHistory") val watchHistory: List<KidHistoryItem> = emptyList(),
    @SerializedName("youtubeSubscriptions") val youtubeSubscriptions: List<YouTubeChannelSearchResult> = emptyList()
)

data class UpdateYouTubeSubscriptionTrustRequest(
    @SerializedName("trustLevel") val trustLevel: String
)

// ==========================================
// GAMIFICATION / STARS (SINGLE SOURCE OF TRUTH)
// ==========================================

data class KidAchievementStatsDto(
    @SerializedName("videosWatched") val videosWatched: Int = 0,
    @SerializedName("approvedSearches") val approvedSearches: Int = 0,
    @SerializedName("playlistsCreated") val playlistsCreated: Int = 0,
    @SerializedName("playlistItemsCount") val playlistItemsCount: Int = 0
)

data class KidAchievementItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String = "🌟",
    @SerializedName("progress") val progress: Int = 0,
    @SerializedName("target") val target: Int = 1,
    @SerializedName("rewardStars") val rewardStars: Int = 0,
    @SerializedName("unlocked") val unlocked: Boolean = false
)

data class KidAchievementsDto(
    @SerializedName("stars") val stars: Int = 0,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("levelTitle") val levelTitle: String = "Curious Explorer",
    @SerializedName("nextLevelStars") val nextLevelStars: Int = 100,
    @SerializedName("progressPercent") val progressPercent: Int = 0,
    @SerializedName("unlockedCount") val unlockedCount: Int = 0,
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("stats") val stats: KidAchievementStatsDto = KidAchievementStatsDto(),
    @SerializedName("achievements") val achievements: List<KidAchievementItemDto> = emptyList()
)

data class StarAwardRequest(
    @SerializedName("amount") val amount: Int
)

data class StarSpendRequest(
    @SerializedName("cost") val cost: Int
)

// ==========================================
// STORYBOOKS & READ-ALONG MODELS
// ==========================================

data class StoryPageDto(
    @SerializedName("pageNumber") val pageNumber: Int = 1,
    @SerializedName("text") val text: String = "",
    @SerializedName("imageUrl") val imageUrl: String = "",
    @SerializedName("illustrationEmoji") val illustrationEmoji: String? = null,
    @SerializedName("bgColor") val bgColor: String? = null
)

data class StoryBookDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String = "FamiTube Storybooks",
    @SerializedName("description") val description: String = "",
    @SerializedName("coverUrl") val coverUrl: String = "",
    @SerializedName("ageRating") val ageRating: String = "TODDLER",
    @SerializedName("language") val language: String = "en",
    @SerializedName("category") val category: String = "Stories & Reading",
    @SerializedName("pages") val pages: List<StoryPageDto> = emptyList(),
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("rewardFamibux") val rewardFamibux: Int = 25,
    @SerializedName("isApproved") val isApproved: Boolean = true,
    @SerializedName("createdAt") val createdAt: String = ""
)

data class CompleteStoryRequest(
    @SerializedName("mode") val mode: String = "READ_ALONG"
)

data class CompleteStoryResponseDto(
    @SerializedName("awardedFamibux") val awardedFamibux: Int = 0,
    @SerializedName("alreadyRewardedToday") val alreadyRewardedToday: Boolean = false,
    @SerializedName("storiesReadCount") val storiesReadCount: Int = 0,
    @SerializedName("mode") val mode: String = "READ_ALONG",
    @SerializedName("summary") val summary: KidAchievementsDto? = null
)

data class ClientCrashReportDto(
    @SerializedName("appVersion") val appVersion: String = "",
    @SerializedName("deviceModel") val deviceModel: String = "",
    @SerializedName("osVersion") val osVersion: String = "",
    @SerializedName("currentRoute") val currentRoute: String = "",
    @SerializedName("errorType") val errorType: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("stackTrace") val stackTrace: String = "",
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userRole") val userRole: String? = null,
    @SerializedName("timestamp") val timestamp: String = ""
)

data class StoryCategoryInfoDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("emoji") val emoji: String = "📖",
    @SerializedName("description") val description: String = "",
    @SerializedName("count") val count: Int = 0
)
