package com.kidstube.app.data.repository

import com.kidstube.app.data.local.ParentalPreferences
import com.kidstube.app.data.model.*
import com.kidstube.app.data.remote.KidsTubeApiService
import com.kidstube.app.data.remote.NetworkModule

class KidsTubeRepository(
    private val preferences: ParentalPreferences
) {
    private val api: KidsTubeApiService
        get() = NetworkModule.getApiService(preferences.backendUrl)

    suspend fun getVideos(
        category: String? = null,
        channelId: String? = null,
        ageRating: String? = null,
        search: String? = null,
        featured: Boolean? = null
    ): Result<List<Video>> {
        return try {
            val filterAge = if (ageRating == "ALL" || ageRating == null) null else ageRating
            val res = api.getVideos(
                category = category,
                channelId = channelId,
                ageRating = filterAge,
                search = search,
                featured = featured
            )
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load videos"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetail(id: String): Result<VideoDetailResponse> {
        return try {
            val res = api.getVideoDetail(id)
            if (res.success && res.data != null) {
                // record to watch history
                preferences.addToHistory(id)
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Video not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategories(): Result<List<Category>> {
        return try {
            val res = api.getCategories()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannels(): Result<List<Channel>> {
        return try {
            val res = api.getChannels()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load channels"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelDetail(id: String): Result<ChannelDetailResponse> {
        return try {
            val res = api.getChannelDetail(id)
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Channel not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveChannelState(id: String): Result<LiveChannelState> {
        return try {
            val res = api.getLiveChannelState(id)
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Live channel unavailable"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val res = api.getPlaylists()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load playlists"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAppConfig(): Result<AppConfig> {
        return try {
            val res = api.getAppConfig()
            if (res.success && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.message ?: "Failed to load config"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavoriteVideos(): Result<List<Video>> {
        val favIds = preferences.getFavoriteIds()
        if (favIds.isEmpty()) return Result.success(emptyList())

        return try {
            val allRes = api.getVideos()
            if (allRes.success && allRes.data != null) {
                val favList = allRes.data.filter { favIds.contains(it.id) }
                Result.success(favList)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistoryVideos(): Result<List<Video>> {
        val histIds = preferences.getHistoryIds()
        if (histIds.isEmpty()) return Result.success(emptyList())

        return try {
            val allRes = api.getVideos()
            if (allRes.success && allRes.data != null) {
                val idMap = allRes.data.associateBy { it.id }
                val ordered = histIds.mapNotNull { idMap[it] }
                Result.success(ordered)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isFavorite(videoId: String): Boolean = preferences.isFavorite(videoId)

    fun toggleFavorite(videoId: String): Boolean = preferences.toggleFavorite(videoId)

    fun getPreferences(): ParentalPreferences = preferences
}
