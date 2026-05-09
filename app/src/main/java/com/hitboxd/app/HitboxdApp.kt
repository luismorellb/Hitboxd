package com.hitboxd.app

import android.app.Application
import com.hitboxd.app.data.network.RetrofitClient

class HitboxdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
}
