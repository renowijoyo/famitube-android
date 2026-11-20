package com.famitube.app.data.remote

import com.famitube.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FamiTubeApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("auth/me")
    suspend fun getAuthMe(): Response<AuthMeResponse>

    @GET("subscription/entitlements")
    suspend fun getEntitlements(): Response<ApiResponse<PlanEntitlementsDto>>

    @POST("subscription/plan")
    suspend fun updateSubscriptionPlan(@Body body: UpdatePlanRequest): Response<ApiResponse<PlanEntitlementsDto>>

    @GET("tags/taxonomy")
    suspend fun getTagTaxonomy(): ApiResponse<TagTaxonomyData>

    @GET("kids/feed")
    suspend fun getKidsFeed(
        @Query("includeTags") includeTags: String? = null,
        @Query("anyTags") anyTags: String? = null,
        @Query("excludeTags") excludeTags: String? = null,
        @Query("preset") preset: String? = null,
        @Query("ageRating") ageRating: String? = null
    ): ApiResponse<KidsFeedData>

    @GET("videos")
    suspend fun getVideos(
        @Query("category") category: String? = null,
        @Query("channelId") channelId: String? = null,
        @Query("ageRating") ageRating: String? = null,
        @Query("search") search: String? = null,
        @Query("featured") featured: Boolean? = null,
        @Query("includeTags") includeTags: String? = null,
        @Query("anyTags") anyTags: String? = null,
        @Query("excludeTags") excludeTags: String? = null,
        @Query("preset") preset: String? = null
    ): ApiResponse<List<Video>>

    @GET("videos/{id}")
    suspend fun getVideoDetail(
        @Path("id") id: String
    ): ApiResponse<VideoDetailResponse>

    @GET("categories")
    suspend fun getCategories(): ApiResponse<List<Category>>

    @GET("channels")
    suspend fun getChannels(
        @Query("ageRating") ageRating: String? = null
    ): ApiResponse<List<Channel>>

    @GET("channels/{id}")
    suspend fun getChannelDetail(
        @Path("id") id: String,
        @Query("ageRating") ageRating: String? = null
    ): ApiResponse<ChannelDetailResponse>

    @GET("channels/{id}/live")
    suspend fun getLiveChannelState(
        @Path("id") id: String
    ): ApiResponse<LiveChannelState>

    @GET("playlists")
    suspend fun getPlaylists(): ApiResponse<List<Playlist>>

    @GET("playlists/{id}")
    suspend fun getPlaylistDetail(
        @Path("id") id: String
    ): ApiResponse<Map<String, Any>>

    @GET("config")
    suspend fun getAppConfig(): ApiResponse<AppConfig>

    // Kid Saved Channels
    @GET("kids/channels")
    suspend fun getKidSavedChannels(): ApiResponse<List<Channel>>

    @POST("kids/channels/{channelId}")
    suspend fun addKidSavedChannel(
        @Path("channelId") channelId: String
    ): ApiResponse<Any>

    @DELETE("kids/channels/{channelId}")
    suspend fun removeKidSavedChannel(
        @Path("channelId") channelId: String
    ): ApiResponse<Any>

    // Kid YouTube Search & Approval
    @GET("kids/youtube-search")
    suspend fun searchYouTube(
        @Query("q") query: String,
        @Query("region") region: String? = null,
        @Query("lang") lang: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): Response<ApiResponse<YouTubeSearchData>>

    @POST("kids/youtube-search/watch")
    suspend fun requestOrWatchYouTube(
        @Body body: YouTubeWatchRequest
    ): Response<ApiResponse<YouTubeWatchData>>

    @GET("kids/youtube-subscriptions")
    suspend fun getKidYouTubeSubscriptions(): ApiResponse<List<YouTubeChannelSearchResult>>

    @POST("kids/youtube-subscriptions")
    suspend fun subscribeKidYouTubeChannel(
        @Body body: SubscribeYouTubeChannelRequest
    ): ApiResponse<YouTubeChannelSearchResult>

    @DELETE("kids/youtube-subscriptions/{handle}")
    suspend fun unsubscribeKidYouTubeChannel(
        @Path("handle") handle: String
    ): ApiResponse<Any>

    @GET("kids/youtube-channels/{handle}")
    suspend fun getKidYouTubeChannelVideos(
        @Path("handle") handle: String
    ): ApiResponse<YouTubeChannelDetailData>

    @GET("kids/youtube-playlists/{playlistId}")
    suspend fun getKidYouTubePlaylistVideos(
        @Path("playlistId") playlistId: String,
        @Query("handle") handle: String? = null
    ): ApiResponse<YouTubePlaylistDetailData>

    // Kid Server Watch History
    @GET("kids/history")
    suspend fun getKidHistory(): ApiResponse<List<KidHistoryItem>>

    @POST("kids/history")
    suspend fun recordKidHistory(
        @Body body: KidHistoryPostRequest
    ): ApiResponse<KidHistoryItem>

    // Kid Personal Playlists
    @GET("kids/playlists")
    suspend fun getKidPlaylists(): ApiResponse<List<KidPlaylist>>

    @POST("kids/playlists")
    suspend fun createKidPlaylist(
        @Body body: CreateKidPlaylistRequest
    ): ApiResponse<KidPlaylist>

    @POST("kids/playlists/{playlistId}/items")
    suspend fun addKidPlaylistItem(
        @Path("playlistId") playlistId: String,
        @Body body: AddKidPlaylistItemRequest
    ): ApiResponse<KidPlaylist>

    @DELETE("kids/playlists/{playlistId}/items/{itemKey}")
    suspend fun removeKidPlaylistItem(
        @Path("playlistId") playlistId: String,
        @Path("itemKey") itemKey: String
    ): ApiResponse<KidPlaylist>

    @DELETE("kids/playlists/{playlistId}")
    suspend fun deleteKidPlaylist(
        @Path("playlistId") playlistId: String
    ): ApiResponse<Any>

    // Parent Mode Endpoints
    @GET("parent/controls")
    suspend fun getParentGlobalControls(): ApiResponse<GlobalParentalControlsDto>

    @PUT("parent/controls")
    suspend fun updateParentGlobalControls(
        @Body body: GlobalParentalControlsDto
    ): Response<ApiResponse<GlobalParentalControlsDto>>

    @GET("parent/children")
    suspend fun getParentChildren(): ApiResponse<List<ParentChildDto>>

    @POST("parent/children")
    suspend fun registerParentChild(
        @Body body: RegisterChildRequest
    ): Response<ApiResponse<ParentChildDto>>

    @PUT("parent/children/{childId}")
    suspend fun updateParentChild(
        @Path("childId") childId: String,
        @Body body: UpdateChildControlsRequest
    ): Response<ApiResponse<ParentChildDto>>

    @DELETE("parent/children/{childId}")
    suspend fun deleteParentChild(
        @Path("childId") childId: String
    ): ApiResponse<Any>

    @GET("parent/video-requests")
    suspend fun getParentVideoRequests(): ApiResponse<List<ParentVideoRequestDto>>

    @POST("parent/video-requests/{requestId}/decide")
    suspend fun decideParentVideoRequest(
        @Path("requestId") requestId: String,
        @Body body: DecideVideoRequestBody
    ): Response<ApiResponse<ParentVideoRequestDto>>

    // Embedded Browser Endpoints
    @GET("kids/browser/config")
    suspend fun getKidBrowserConfig(): ApiResponse<BrowserConfigDto>

    @POST("kids/browser/history")
    suspend fun recordKidBrowserHistory(
        @Body body: BrowserHistoryBatchRequest
    ): ApiResponse<List<KidBrowserHistoryItemDto>>

    @POST("kids/browser/requests")
    suspend fun requestSiteAccess(
        @Body body: CreateSiteRequestBody
    ): Response<ApiResponse<KidSiteRequestDto>>

    @GET("parent/children/{childId}/browser-history")
    suspend fun getChildBrowserHistory(
        @Path("childId") childId: String,
        @Query("limit") limit: Int = 200
    ): ApiResponse<List<KidBrowserHistoryItemDto>>

    @GET("parent/children/{childId}/activity-history")
    suspend fun getChildActivityHistory(
        @Path("childId") childId: String
    ): ApiResponse<KidActivityHistoryData>

    @PUT("parent/children/{childId}/youtube-subscriptions/{handle}")
    suspend fun updateChildYouTubeSubscriptionTrust(
        @Path("childId") childId: String,
        @Path("handle") handle: String,
        @Body body: UpdateYouTubeSubscriptionTrustRequest
    ): ApiResponse<YouTubeChannelSearchResult>

    @DELETE("parent/children/{childId}/youtube-subscriptions/{handle}")
    suspend fun deleteChildYouTubeSubscription(
        @Path("childId") childId: String,
        @Path("handle") handle: String
    ): ApiResponse<Any>

    @DELETE("parent/children/{childId}/search-history")
    suspend fun clearChildSearchHistory(
        @Path("childId") childId: String
    ): ApiResponse<Any>

    @DELETE("parent/children/{childId}/watch-history")
    suspend fun clearChildWatchHistory(
        @Path("childId") childId: String
    ): ApiResponse<Any>

    @DELETE("parent/children/{childId}/browser-history")
    suspend fun clearChildBrowserHistory(
        @Path("childId") childId: String
    ): ApiResponse<Any>

    @GET("parent/site-requests")
    suspend fun getParentSiteRequests(): ApiResponse<List<KidSiteRequestDto>>

    @POST("parent/site-requests/{requestId}/decide")
    suspend fun decideParentSiteRequest(
        @Path("requestId") requestId: String,
        @Body body: DecideSiteRequestBody
    ): Response<ApiResponse<KidSiteRequestDto>>

    // Notifications & Notification History
    @GET("notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 100
    ): ApiResponse<NotificationsDataDto>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(
        @Path("id") id: String
    ): ApiResponse<Any>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): ApiResponse<Any>

    @DELETE("notifications")
    suspend fun clearNotifications(): ApiResponse<Any>

    // Gamification / Stars (Single Source of Truth)
    @GET("kids/achievements")
    suspend fun getKidAchievements(): ApiResponse<KidAchievementsDto>

    @POST("kids/stars/award")
    suspend fun awardKidStars(
        @Body body: StarAwardRequest
    ): ApiResponse<KidAchievementsDto>

    @POST("kids/stars/spend")
    suspend fun spendKidStars(
        @Body body: StarSpendRequest
    ): ApiResponse<KidAchievementsDto>
}
