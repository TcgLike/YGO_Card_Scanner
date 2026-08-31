package com.ygocardscanner

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.ygocardscanner.di.AppContainer

class YgoCardScannerApplication : Application(), Configuration.Provider {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(appContainer.workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}

