package com.famitube.app

import android.app.Application
import com.famitube.app.data.local.AchievementManager
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.repository.FamiTubeRepository

class FamiTubeApp : Application() {

    lateinit var preferences: ParentalPreferences
        private set

    lateinit var achievementManager: AchievementManager
        private set

    lateinit var repository: FamiTubeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = ParentalPreferences(this)
        achievementManager = AchievementManager(this, preferences)
        repository = FamiTubeRepository(preferences, achievementManager)
    }
}
