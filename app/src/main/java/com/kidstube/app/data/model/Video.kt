package com.kidstube.app.data.model

import com.google.gson.annotations.SerializedName

enum class SourceType {
    @SerializedName("YOUTUBE")
    YOUTUBE,

    @SerializedName("DIRECT")
    DIRECT
}

enum class AgeRating(val label: String) {
    @SerializedName("ALL")
    ALL("All Ages"),

    @SerializedName("TODDLER")
    TODDLER("Toddlers (2-4)"),

    @SerializedName("YOUNG_KIDS")
    YOUNG_KIDS("Young Kids (5-8)"),

    @SerializedName("OLDER_KIDS")
    OLDER_KIDS("Older Kids (9-12)")
}

data class Video(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("sourceType") val sourceType: SourceType,
    @SerializedName("sourceUrl") val sourceUrl: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("duration") val duration: Int = 180, // in seconds
    @SerializedName("channelId") val channelId: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("channelAvatar") val channelAvatar: String,
    @SerializedName("category") val category: String,
    @SerializedName("ageRating") val ageRating: AgeRating = AgeRating.ALL,
    @SerializedName("isFeatured") val isFeatured: Boolean = false,
    @SerializedName("isApproved") val isApproved: Boolean = true,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("createdAt") val createdAt: String = ""
)

data class VideoDetailResponse(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("sourceType") val sourceType: SourceType,
    @SerializedName("sourceUrl") val sourceUrl: String,
    @SerializedName("youtubeVideoId") val youtubeVideoId: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String,
    @SerializedName("duration") val duration: Int = 180,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("channelAvatar") val channelAvatar: String,
    @SerializedName("category") val category: String,
    @SerializedName("ageRating") val ageRating: AgeRating = AgeRating.ALL,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("relatedVideos") val relatedVideos: List<Video> = emptyList()
)
