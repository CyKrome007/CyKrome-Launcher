package com.cykrome.launcher.util

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

object RootHelper {
    
    private const val TAG = "RootHelper"
    private var isRooted: Boolean? = null
    private var hasRootAccess: Boolean? = null
    
    /**
     * Check if device is rooted
     */
    fun isRooted(): Boolean {
        if (isRooted != null) return isRooted!!
        
        isRooted = checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
        return isRooted!!
    }
    
    /**
     * Check if we have root access (can execute su commands)
     */
    fun hasRootAccess(): Boolean {
        if (hasRootAccess != null) return hasRootAccess!!
        
        if (!isRooted()) {
            hasRootAccess = false
            return false
        }
        
        hasRootAccess = try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.d(TAG, "Root access check failed: ${e.message}")
            false
        }
        
        return hasRootAccess!!
    }
    
    /**
     * Execute a command with root privileges
     */
    fun executeRootCommand(command: String): Boolean {
        if (!hasRootAccess()) {
            Log.w(TAG, "No root access available")
            return false
        }
        
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: ${e.message}", e)
            false
        }
    }
    
    /**
     * Install app as system app (requires root)
     */
    fun installAsSystemApp(packageName: String, apkPath: String): Boolean {
        if (!hasRootAccess()) return false
        
        val commands = arrayOf(
            "mount -o remount,rw /system",
            "cp $apkPath /system/app/",
            "chmod 644 /system/app/${apkPath.substringAfterLast("/")}",
            "mount -o remount,ro /system"
        )
        
        return commands.all { executeRootCommand(it) }
    }
    
    /**
     * Grant all permissions to app (requires root)
     */
    fun grantAllPermissions(packageName: String): Boolean {
        if (!hasRootAccess()) return false
        
        return executeRootCommand("pm grant $packageName android.permission.QUERY_ALL_PACKAGES") &&
               executeRootCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }
    
    /**
     * Enable notification listener via root (if permission denied)
     */
    fun enableNotificationListener(packageName: String, componentName: String): Boolean {
        if (!hasRootAccess()) return false
        
        return executeRootCommand("pm enable $packageName/$componentName")
    }
    
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { java.io.File(it).exists() }
    }
    
    private fun checkRootMethod2(): Boolean {
        return try {
            Runtime.getRuntime().exec("which su").inputStream.use { it.readBytes().isNotEmpty() }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootMethod3(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
}

