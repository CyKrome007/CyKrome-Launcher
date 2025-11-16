package com.cykrome.launcher.util

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.os.Build

object ShortcutHelper {
    
    /**
     * Get shortcuts for a given package using LauncherApps API
     */
    fun getShortcuts(context: Context, packageName: String): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return emptyList()
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                if (launcherApps != null) {
                    val userHandle = android.os.Process.myUserHandle()
                    // Create a query for shortcuts
                    val query = android.content.pm.LauncherApps.ShortcutQuery()
                    query.setPackage(packageName)
                    query.setQueryFlags(
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                    launcherApps.getShortcuts(query, userHandle) ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("ShortcutHelper", "Error getting shortcuts for $packageName: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get shortcut icon
     */
    fun getShortcutIcon(context: Context, shortcut: ShortcutInfo, packageName: String): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return null
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                // Try to get icon from shortcut
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                if (launcherApps != null) {
                    val userHandle = android.os.Process.myUserHandle()
                    try {
                        launcherApps.getShortcutIconDrawable(shortcut, 0)
                    } catch (e: Exception) {
                        // Fallback to app icon
                        try {
                            val packageManager = context.packageManager
                            packageManager.getApplicationIcon(packageName)
                        } catch (e2: Exception) {
                            null
                        }
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ShortcutHelper", "Error getting shortcut icon: ${e.message}", e)
            null
        }
    }
    
    /**
     * Launch a shortcut
     */
    fun launchShortcut(context: Context, shortcut: ShortcutInfo, packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            if (launcherApps != null) {
                val userHandle = android.os.Process.myUserHandle()
                launcherApps.startShortcut(packageName, shortcut.id, null, null, userHandle)
                
                // Report shortcut usage
                val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as? ShortcutManager
                shortcutManager?.reportShortcutUsed(shortcut.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("ShortcutHelper", "Error launching shortcut: ${e.message}", e)
        }
    }
}

