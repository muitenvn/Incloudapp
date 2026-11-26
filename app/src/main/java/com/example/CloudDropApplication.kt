package com.example

import android.app.Application
import com.example.cloud.CloudUploadEngine
import com.example.data.CloudDropRepository
import com.example.data.local.CloudDropDatabase
import com.example.data.local.CloudPreferencesRepository
import com.example.update.AppUpdateManager

class CloudDropApplication : Application() {

    lateinit var repository: CloudDropRepository
        private set

    lateinit var updateManager: AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        val database = CloudDropDatabase.getInstance(this)
        val preferences = CloudPreferencesRepository(this)
        val engine = CloudUploadEngine(this)
        repository = CloudDropRepository(
            historyDao = database.uploadHistoryDao(),
            preferencesRepository = preferences,
            uploadEngine = engine
        )
        updateManager = AppUpdateManager(this)
    }
}
