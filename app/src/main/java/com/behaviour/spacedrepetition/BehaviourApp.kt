package com.behaviour.spacedrepetition

import android.app.Application
import com.behaviour.spacedrepetition.auth.AppwriteClient
import com.behaviour.spacedrepetition.scheduling.NotificationWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BehaviourApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Appwrite client
        AppwriteClient.init(this)

        // Schedule periodic revision check worker
        NotificationWorker.schedule(this)
    }
}
