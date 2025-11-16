package com.cykrome.launcher.model

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val applicationInfo: ApplicationInfo,
    var badgeCount: Int = 0
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
    
    fun getLaunchIntent(packageManager: PackageManager): android.content.Intent? {
        return packageManager.getLaunchIntentForPackage(packageName)?.apply {
            component = componentName
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }
}

