package com.cykrome.launcher.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.cykrome.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLoader {
    
    suspend fun loadApps(context: Context, hiddenPackages: Set<String> = emptySet()): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            
            // Use QUERY_ALL_PACKAGES flag if available and permission granted, or if root is available
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    if (context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        PackageManager.MATCH_ALL
                    } else if (RootHelper.hasRootAccess()) {
                        // Try to grant permission via root
                        RootHelper.grantAllPermissions(context.packageName)
                        PackageManager.MATCH_ALL
                    } else {
                        PackageManager.MATCH_DEFAULT_ONLY
                    }
                } catch (e: Exception) {
                    PackageManager.MATCH_DEFAULT_ONLY
                }
            } else {
                PackageManager.MATCH_DEFAULT_ONLY
            }
            
            val resolveInfos: List<ResolveInfo> = try {
                packageManager.queryIntentActivities(intent, flags)
            } catch (e: Exception) {
                android.util.Log.e("AppLoader", "Error querying apps: ${e.message}", e)
                // Fallback to default only if MATCH_ALL failed
                if (flags == PackageManager.MATCH_ALL) {
                    try {
                        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    } catch (e2: Exception) {
                        android.util.Log.e("AppLoader", "Fallback query also failed: ${e2.message}", e2)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            
            val loadedApps = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                
                // Skip hidden apps
                if (hiddenPackages.contains(packageName)) {
                    return@mapNotNull null
                }
                
                // Skip launcher main activity, but include Settings
                if (packageName == context.packageName) {
                    // Only include SettingsActivity, skip LauncherActivity
                    if (activityName.contains("SettingsActivity")) {
                        try {
                            val label = resolveInfo.loadLabel(packageManager).toString()
                            val icon = resolveInfo.loadIcon(packageManager)
                            AppInfo(
                                packageName = packageName,
                                activityName = activityName,
                                label = label,
                                icon = icon,
                                applicationInfo = resolveInfo.activityInfo.applicationInfo
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    try {
                        val label = resolveInfo.loadLabel(packageManager).toString()
                        val icon = resolveInfo.loadIcon(packageManager)
                        AppInfo(
                            packageName = packageName,
                            activityName = activityName,
                            label = label,
                            icon = icon,
                            applicationInfo = resolveInfo.activityInfo.applicationInfo
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }.sortedBy { it.label.lowercase() }
            
            loadedApps
        } catch (e: Exception) {
            android.util.Log.e("AppLoader", "Error loading apps: ${e.message}", e)
            emptyList()
        }
    }
    
    fun getIconPackIcon(context: Context, iconPackPackage: String, appInfo: AppInfo): android.graphics.drawable.Drawable? {
        if (iconPackPackage.isEmpty()) return null
        
        return try {
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

