package com.famitube.app.data.remote

import com.famitube.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FamiTubeApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("auth/me")
    suspend fun getAuthMe(): Response<AuthMeResponse>

    @GET("tags/taxonomy")
    suspend fun getTagTaxonomy(): ApiResponse<TagTaxonomyData>

    @GET("kids/feed")
    suspend fun getKidsFeed(
        @Query("includeTags") includeTags: String? = null,
        @Query("anyTags") anyTags: String? = null,
        @Query("excludeTags") excludeTags: String? = null,
        @Query("preset") preset: String? = null
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
    suspend fun getChannels(): ApiResponse<List<Channel>>

    @GET("channels/{id}")
    suspend fun getChannelDetail(
        @Path("id") id: String
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

    // Channel Quiz Wall
    @GET("kids/channels/{channelId}/access")
    suspend fun getChannelAccess(
        @Path("channelId") channelId: String
    ): ApiResponse<ChannelAccessData>

    @POST("kids/channels/{channelId}/quiz-answer")
    suspend fun submitChannelQuizAnswer(
        @Path("channelId") channelId: String,
        @Body body: QuizAnswerRequest
    ): Response<ApiResponse<QuizAnswerData>>

    // Kid YouTube Search & Approval
    @GET("kids/youtube-search")
    suspend fun searchYouTube(
        @Query("q") query: String
    ): Response<ApiResponse<YouTubeSearchData>>

    @POST("kids/youtube-search/watch")
    suspend fun requestOrWatchYouTube(
        @Body body: YouTubeWatchRequest
    ): Response<ApiResponse<YouTubeWatchData>>

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
}
