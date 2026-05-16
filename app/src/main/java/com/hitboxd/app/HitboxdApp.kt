package com.hitboxd.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.hitboxd.app.data.network.RetrofitClient

class HitboxdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hitboxd_social",
                "Social",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
