package com.cykrome.launcher.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.cykrome.launcher.data.LauncherPreferences
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object NovaBackupParser {
    
    /**
     * Checks if a file is a Nova Launcher backup file
     */
    fun isNovaBackup(file: File): Boolean {
        return file.name.endsWith(".novabackup", ignoreCase = true) ||
               file.name.endsWith(".nova", ignoreCase = true) ||
               file.extension.isEmpty() // Nova sometimes saves without extension
    }
    
    /**
     * Restores settings from a Nova Launcher backup file
     */
    fun restoreFromNovaBackup(context: Context, file: File): Boolean {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            
            // Nova backups are ZIP files containing XML preference files
            ZipInputStream(FileInputStream(file)).use { zipInputStream ->
                var entry: ZipEntry? = zipInputStream.nextEntry
                
                while (entry != null) {
                    // Look for preferences XML file
                    if (entry.name.contains("preferences", ignoreCase = true) ||
                        entry.name.endsWith(".xml", ignoreCase = true)) {
                        
                        parseNovaPreferences(zipInputStream, editor)
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            
            editor.apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Parses Nova Launcher preferences XML and maps them to our preferences
     */
    private fun parseNovaPreferences(inputStream: java.io.InputStream, editor: SharedPreferences.Editor) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            
            var eventType = parser.eventType
            var currentKey: String? = null
            var currentValue: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "string", "int", "boolean", "float", "long" -> {
                                currentKey = parser.getAttributeValue(null, "name")
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentKey != null) {
                            currentValue = parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (currentKey != null && currentValue != null) {
                            mapNovaPreference(parser.name, currentKey, currentValue, editor)
                            currentKey = null
                            currentValue = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Maps Nova Launcher preference keys to our launcher's preference keys
     */
    private fun mapNovaPreference(
        type: String,
        novaKey: String,
        novaValue: String,
        editor: SharedPreferences.Editor
    ) {
        // Map Nova Launcher keys to our keys
        val mappedKey = mapNovaKey(novaKey) ?: return
        
        try {
            when (type) {
                "int" -> {
                    val intValue = novaValue.toInt()
                    editor.putInt(mappedKey, mapNovaIntValue(novaKey, intValue))
                }
                "boolean" -> {
                    val boolValue = novaValue.toBoolean()
                    editor.putBoolean(mappedKey, boolValue)
                }
                "float" -> {
                    val floatValue = novaValue.toFloat()
                    editor.putFloat(mappedKey, mapNovaFloatValue(novaKey, floatValue))
                }
                "string" -> {
                    editor.putString(mappedKey, mapNovaStringValue(novaKey, novaValue))
                }
            }
        } catch (e: Exception) {
            // Skip invalid values
        }
    }
    
    /**
     * Maps Nova Launcher preference keys to our preference keys
     */
    private fun mapNovaKey(novaKey: String): String? {
        return when {
            // Grid settings
            novaKey.contains("desktop_grid", ignoreCase = true) ||
            novaKey.contains("grid_x", ignoreCase = true) -> LauncherPreferences.KEY_HOME_GRID_COLUMNS
            
            novaKey.contains("grid_y", ignoreCase = true) ||
            novaKey.contains("desktop_rows", ignoreCase = true) -> LauncherPreferences.KEY_HOME_GRID_ROWS
            
            novaKey.contains("drawer_grid", ignoreCase = true) -> LauncherPreferences.KEY_DRAWER_GRID_COLUMNS
            
            // Icon settings
            novaKey.contains("icon_size", ignoreCase = true) ||
            novaKey.contains("desktop_icon_size", ignoreCase = true) -> LauncherPreferences.KEY_ICON_SIZE
            
            novaKey.contains("show_labels", ignoreCase = true) ||
            novaKey.contains("desktop_label", ignoreCase = true) -> LauncherPreferences.KEY_SHOW_ICON_LABELS
            
            // Drawer settings
            novaKey.contains("drawer_style", ignoreCase = true) ||
            novaKey.contains("app_drawer_style", ignoreCase = true) -> LauncherPreferences.KEY_DRAWER_STYLE
            
            // Hidden apps
            novaKey.contains("hidden_apps", ignoreCase = true) ||
            novaKey.contains("excluded_apps", ignoreCase = true) -> LauncherPreferences.KEY_HIDDEN_APPS
            
            // Dock settings
            novaKey.contains("dock_icon_count", ignoreCase = true) -> LauncherPreferences.KEY_DOCK_ICONS
            novaKey.contains("scrollable_dock", ignoreCase = true) -> LauncherPreferences.KEY_SCROLLABLE_DOCK
            
            // Gestures
            novaKey.contains("gesture_swipe_up", ignoreCase = true) ||
            novaKey.contains("swipe_up_action", ignoreCase = true) -> LauncherPreferences.KEY_SWIPE_UP
            
            novaKey.contains("gesture_swipe_down", ignoreCase = true) ||
            novaKey.contains("swipe_down_action", ignoreCase = true) -> LauncherPreferences.KEY_SWIPE_DOWN
            
            novaKey.contains("gesture_double_tap", ignoreCase = true) ||
            novaKey.contains("double_tap_action", ignoreCase = true) -> LauncherPreferences.KEY_DOUBLE_TAP
            
            // Icon pack
            novaKey.contains("icon_pack", ignoreCase = true) ||
            novaKey.contains("icon_theme", ignoreCase = true) -> LauncherPreferences.KEY_ICON_PACK
            
            // Scroll effects
            novaKey.contains("scroll_effect", ignoreCase = true) ||
            novaKey.contains("desktop_scroll", ignoreCase = true) -> LauncherPreferences.KEY_SCROLL_EFFECT
            
            // Animation speed
            novaKey.contains("animation_speed", ignoreCase = true) ||
            novaKey.contains("anim_speed", ignoreCase = true) -> LauncherPreferences.KEY_ANIMATION_SPEED
            
            // Badges
            novaKey.contains("notification_badges", ignoreCase = true) ||
            novaKey.contains("show_badges", ignoreCase = true) -> LauncherPreferences.KEY_SHOW_BADGES
            
            novaKey.contains("unread_count", ignoreCase = true) -> LauncherPreferences.KEY_SHOW_UNREAD_COUNT
            
            // Folder settings
            novaKey.contains("folder_style", ignoreCase = true) -> LauncherPreferences.KEY_FOLDER_STYLE
            novaKey.contains("folder_preview", ignoreCase = true) -> LauncherPreferences.KEY_FOLDER_PREVIEW
            
            else -> null
        }
    }
    
    /**
     * Maps Nova Launcher integer values to our format
     */
    private fun mapNovaIntValue(novaKey: String, novaValue: Int): Int {
        return when {
            novaKey.contains("icon_size", ignoreCase = true) -> {
                // Nova uses different scale, convert to percentage
                when {
                    novaValue < 50 -> 50
                    novaValue > 150 -> 150
                    else -> novaValue
                }
            }
            novaKey.contains("grid", ignoreCase = true) -> {
                // Ensure grid values are in valid range
                when {
                    novaValue < 3 -> 3
                    novaValue > 10 -> 10
                    else -> novaValue
                }
            }
            else -> novaValue
        }
    }
    
    /**
     * Maps Nova Launcher float values to our format
     */
    private fun mapNovaFloatValue(novaKey: String, novaValue: Float): Float {
        return when {
            novaKey.contains("animation_speed", ignoreCase = true) -> {
                // Clamp animation speed
                when {
                    novaValue < 0.5f -> 0.5f
                    novaValue > 2.0f -> 2.0f
                    else -> novaValue
                }
            }
            else -> novaValue
        }
    }
    
    /**
     * Maps Nova Launcher string values to our format
     */
    private fun mapNovaStringValue(novaKey: String, novaValue: String): String {
        return when {
            novaKey.contains("drawer_style", ignoreCase = true) -> {
                // Map Nova drawer styles to ours
                when {
                    novaValue.contains("vertical", ignoreCase = true) -> LauncherPreferences.DRAWER_STYLE_VERTICAL
                    novaValue.contains("horizontal", ignoreCase = true) -> LauncherPreferences.DRAWER_STYLE_HORIZONTAL
                    novaValue.contains("list", ignoreCase = true) -> LauncherPreferences.DRAWER_STYLE_LIST
                    else -> LauncherPreferences.DRAWER_STYLE_VERTICAL
                }
            }
            novaKey.contains("scroll_effect", ignoreCase = true) -> {
                // Map Nova scroll effects to ours
                when {
                    novaValue.contains("cube", ignoreCase = true) -> LauncherPreferences.SCROLL_EFFECT_CUBE
                    novaValue.contains("cylinder", ignoreCase = true) -> LauncherPreferences.SCROLL_EFFECT_CYLINDER
                    novaValue.contains("carousel", ignoreCase = true) -> LauncherPreferences.SCROLL_EFFECT_CAROUSEL
                    else -> LauncherPreferences.SCROLL_EFFECT_NONE
                }
            }
            novaKey.contains("gesture", ignoreCase = true) || novaKey.contains("action", ignoreCase = true) -> {
                // Map Nova gesture actions to ours
                when {
                    novaValue.contains("drawer", ignoreCase = true) ||
                    novaValue.contains("app_drawer", ignoreCase = true) -> LauncherPreferences.ACTION_APP_DRAWER
                    novaValue.contains("search", ignoreCase = true) -> LauncherPreferences.ACTION_SEARCH
                    novaValue.contains("notification", ignoreCase = true) -> LauncherPreferences.ACTION_NOTIFICATIONS
                    else -> LauncherPreferences.ACTION_NONE
                }
            }
            else -> novaValue
        }
    }
}

