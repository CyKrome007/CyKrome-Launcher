package com.cykrome.launcher.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.cykrome.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLoader {
    
    suspend fun loadApps(context: Context, hiddenPackages: Set<String> = emptySet()): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        
        resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val activityName = resolveInfo.activityInfo.name
            
            // Skip hidden apps
            if (hiddenPackages.contains(packageName)) {
                return@mapNotNull null
            }
            
            // Skip launcher itself
            if (packageName == context.packageName) {
                return@mapNotNull null
            }
            
            try {
                val label = resolveInfo.loadLabel(packageManager).toString()
                val icon = resolveInfo.loadIcon(packageManager)
                val appInfo = AppInfo(
                    packageName = packageName,
                    activityName = activityName,
                    label = label,
                    icon = icon,
                    applicationInfo = resolveInfo.activityInfo.applicationInfo
                )
                appInfo
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }
    
    fun getIconPackIcon(context: Context, iconPackPackage: String, appInfo: AppInfo): android.graphics.drawable.Drawable? {
        if (iconPackPackage.isEmpty()) return null
        
        return try {
            val packageManager = context.packageManager
            val iconPackContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
            val resources = iconPackContext.resources
            
            // Try to get icon from icon pack
            val resourceId = resources.getIdentifier(
                appInfo.packageName.replace(".", "_"),
                "drawable",
                iconPackPackage
            )
            
            if (resourceId != 0) {
                resources.getDrawable(resourceId, null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

