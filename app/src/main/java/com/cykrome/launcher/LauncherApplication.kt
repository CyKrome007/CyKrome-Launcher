package com.cykrome.launcher

import android.app.Application
import android.content.Context

class LauncherApplication : Application() {
    
    companion object {
        @Volatile
        private var instance: LauncherApplication? = null
        
        fun getInstance(): LauncherApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        fun getContext(): Context {
            return getInstance().applicationContext
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

