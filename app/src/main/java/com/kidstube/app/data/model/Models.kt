package com.kidstube.app.data.model

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
