package com.example

import android.app.Application
import com.example.data.local.FloraPulseDatabase
import com.example.data.repository.TelemetryRepository

class FloraPulseApp : Application() {
    val database by lazy { FloraPulseDatabase.getInstance(this) }
    val repository by lazy { TelemetryRepository(database.telemetryDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FloraPulseApp
            private set
    }
}
