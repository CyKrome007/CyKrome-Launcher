package com.cykrome.launcher.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object BackupRestoreHelper {
    
    /**
     * Backup file format types
     */
    enum class BackupFormat {
        CYKROME_JSON,  // Our native JSON format
        NOVA_BACKUP     // Nova Launcher backup format
    }
    
    /**
     * Detects the backup file format
     */
    fun detectBackupFormat(file: File): BackupFormat {
        return when {
            NovaBackupParser.isNovaBackup(file) -> BackupFormat.NOVA_BACKUP
            file.name.endsWith(".json", ignoreCase = true) -> BackupFormat.CYKROME_JSON
            else -> {
                // Try to detect by content
                try {
                    FileReader(file).use { reader ->
                        val firstChars = CharArray(100)
                        reader.read(firstChars, 0, 100)
                        val content = String(firstChars)
                        if (content.trimStart().startsWith("{")) {
                            BackupFormat.CYKROME_JSON
                        } else {
                            BackupFormat.NOVA_BACKUP
                        }
                    }
                } catch (e: Exception) {
                    BackupFormat.CYKROME_JSON // Default
                }
            }
        }
    }
    
    fun backupSettings(context: Context, file: File): Boolean {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val allPrefs = prefs.all
            
            val json = Gson().toJson(allPrefs)
            
            FileWriter(file).use { writer ->
                writer.write(json)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Restores settings from a backup file (supports both formats)
     */
    fun restoreSettings(context: Context, file: File): RestoreResult {
        return try {
            val format = detectBackupFormat(file)
            
            val success = when (format) {
                BackupFormat.NOVA_BACKUP -> {
                    NovaBackupParser.restoreFromNovaBackup(context, file)
                }
                BackupFormat.CYKROME_JSON -> {
                    restoreFromJson(context, file)
                }
            }
            
            RestoreResult(success, format, if (success) null else "Failed to restore settings")
        } catch (e: Exception) {
            e.printStackTrace()
            val format = try {
                detectBackupFormat(file)
            } catch (ex: Exception) {
                BackupFormat.CYKROME_JSON
            }
            RestoreResult(false, format, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Restores settings from our JSON format
     */
    private fun restoreFromJson(context: Context, file: File): Boolean {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            
            FileReader(file).use { reader ->
                val json = reader.readText()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = Gson().fromJson(json, type)
                
                map.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(key, value as Set<String>)
                    }
                }
                
                editor.apply()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Result of a restore operation
     */
    data class RestoreResult(
        val success: Boolean,
        val format: BackupFormat,
        val errorMessage: String?
    )
}

