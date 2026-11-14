package com.kidstube.app.data.remote

import com.kidstube.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KidsTubeApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("kids/feed")
    suspend fun getKidsFeed(): ApiResponse<KidsFeedData>

    @GET("videos")
    suspend fun getVideos(
        @Query("category") category: String? = null,
        @Query("channelId") channelId: String? = null,
        @Query("ageRating") ageRating: String? = null,
        @Query("search") search: String? = null,
        @Query("featured") featured: Boolean? = null
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
}
