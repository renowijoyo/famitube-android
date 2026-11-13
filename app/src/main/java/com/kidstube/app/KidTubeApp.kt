package com.kidstube.app

import android.app.Application
import com.kidstube.app.data.local.ParentalPreferences
import com.kidstube.app.data.repository.KidsTubeRepository

class KidsTubeApp : Application() {

    lateinit var preferences: ParentalPreferences
        private set

    lateinit var repository: KidsTubeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = ParentalPreferences(this)
        repository = KidsTubeRepository(preferences)
    }
}
