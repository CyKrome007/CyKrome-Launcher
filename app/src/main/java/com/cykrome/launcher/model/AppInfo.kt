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
        return try {
            // Create intent directly using ComponentName to ensure we launch the specific activity
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                component = componentName
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                // For SettingsActivity, ensure it can open in a separate task
                // The taskAffinity="" in manifest helps, but we also need this flag
                if (activityName.contains("SettingsActivity", ignoreCase = true)) {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppInfo", "Error creating launch intent for $packageName/$activityName: ${e.message}", e)
            null
        }
    }
}

