package com.kidstube.app.data.repository

import com.google.gson.Gson
import com.kidstube.app.data.local.ParentalPreferences
import com.kidstube.app.data.model.*
import com.kidstube.app.data.remote.KidsTubeApiService
import com.kidstube.app.data.remote.NetworkModule

class KidsTubeRepository(
    private val preferences: ParentalPreferences
) {
    init {
        if (preferences.authToken.isNotBlank()) {
            NetworkModule.updateAuthToken(preferences.authToken)
        }
    }

    private val api: KidsTubeApiService
        get() = NetworkModule.getApiService(preferences.backendUrl)

    fun isLoggedIn(): Boolean = preferences.authToken.isNotBlank()

    suspend fun loginChild(email: String, password: String): Result<AuthUser> {
        return try {
            val response = api.login(
                LoginRequest(
                    email = email.trim().lowercase(),
                    password = password,
                    loginType = "CHILD"
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.token.isNullOrBlank() && body.user != null) {
                    preferences.authToken = body.token
                    preferences.loggedInEmail = body.user.email
                    preferences.loggedInName = body.user.name
                    if (!body.user.ageRating.isNullOrBlank()) {
                        preferences.selectedAgeFilter = body.user.ageRating
                    }
                    NetworkModule.updateAuthToken(body.token)
                    Result.success(body.user)
                } else {
                    Result.failure(Exception(body?.message ?: "Login failed"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val errMsg = try {
                    Gson().fromJson(errBody, LoginResponse::class.java)?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(
                    Exception(
                        errMsg
                            ?: "This email/Gmail is not registered as a child account by a parent/guardian."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        preferences.clearAuthSession()
        NetworkModule.updateAuthToken("")
    }

    suspend fun getVideos(
        category: String? = null,
        channelId: String? = null,
        ageRating: String? = null,
        search: String? = null,
        featured: Boolean? = null
    ): Result<List<Video>> {
        return try {
            if (isLoggedIn() && category == null && channelId == null && search == null && featured == null) {
                val feedRes = api.getKidsFeed()
                if (feedRes.success && feedRes.data != null) {
                    return Result.success(feedRes.data.videos)
                }
            }
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
            if (isLoggedIn()) {
                val feedRes = api.getKidsFeed()
                if (feedRes.success && feedRes.data != null) {
                    return Result.success(feedRes.data.channels)
                }
            }
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
