package com.famitube.app

import android.app.Application
import com.famitube.app.data.local.AchievementManager
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.local.StarPointsManager
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.util.CrashReporter

class FamiTubeApp : Application() {

    lateinit var preferences: ParentalPreferences
        private set

    lateinit var starPointsManager: StarPointsManager
        private set

    lateinit var achievementManager: AchievementManager
        private set

    lateinit var repository: FamiTubeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = ParentalPreferences(this)
        CrashReporter.init(this, preferences)
        starPointsManager = StarPointsManager(this, preferences)
        achievementManager = AchievementManager(this, preferences, starPointsManager)
        repository = FamiTubeRepository(preferences, achievementManager, starPointsManager, this)
        CrashReporter.dispatchPendingCrashReports(this, repository)
    }
}
