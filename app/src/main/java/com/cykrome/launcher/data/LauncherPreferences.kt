package com.cykrome.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class LauncherPreferences(context: Context) {
    
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // Home Screen Settings
    var homeGridColumns: Int
        get() = prefs.getInt(KEY_HOME_GRID_COLUMNS, 4)
        set(value) = prefs.edit().putInt(KEY_HOME_GRID_COLUMNS, value).apply()
    
    var homeGridRows: Int
        get() = prefs.getInt(KEY_HOME_GRID_ROWS, 5)
        set(value) = prefs.edit().putInt(KEY_HOME_GRID_ROWS, value).apply()
    
    var iconSize: Int
        get() = prefs.getInt(KEY_ICON_SIZE, 100)
        set(value) = prefs.edit().putInt(KEY_ICON_SIZE, value).apply()
    
    var showIconLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ICON_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ICON_LABELS, value).apply()
    
    var desktopPadding: Int
        get() = prefs.getInt(KEY_DESKTOP_PADDING, 8)
        set(value) = prefs.edit().putInt(KEY_DESKTOP_PADDING, value).apply()
    
    // App Drawer Settings
    var drawerGridColumns: Int
        get() = prefs.getInt(KEY_DRAWER_GRID_COLUMNS, 4)
        set(value) = prefs.edit().putInt(KEY_DRAWER_GRID_COLUMNS, value).apply()
    
    var drawerGridRows: Int
        get() = prefs.getInt(KEY_DRAWER_GRID_ROWS, 6)
        set(value) = prefs.edit().putInt(KEY_DRAWER_GRID_ROWS, value).apply()
    
    var drawerStyle: String
        get() = prefs.getString(KEY_DRAWER_STYLE, DRAWER_STYLE_VERTICAL) ?: DRAWER_STYLE_VERTICAL
        set(value) = prefs.edit().putString(KEY_DRAWER_STYLE, value).apply()
    
    var hiddenApps: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_APPS, value).apply()
    
    // Dock Settings
    var dockIcons: Int
        get() = prefs.getInt(KEY_DOCK_ICONS, 5)
        set(value) = prefs.edit().putInt(KEY_DOCK_ICONS, value).apply()
    
    var scrollableDock: Boolean
        get() = prefs.getBoolean(KEY_SCROLLABLE_DOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_SCROLLABLE_DOCK, value).apply()
    
    // Folder Settings
    var folderStyle: String
        get() = prefs.getString(KEY_FOLDER_STYLE, FOLDER_STYLE_GRID) ?: FOLDER_STYLE_GRID
        set(value) = prefs.edit().putString(KEY_FOLDER_STYLE, value).apply()
    
    var folderPreview: String
        get() = prefs.getString(KEY_FOLDER_PREVIEW, FOLDER_PREVIEW_GRID) ?: FOLDER_PREVIEW_GRID
        set(value) = prefs.edit().putString(KEY_FOLDER_PREVIEW, value).apply()
    
    // Gesture Settings
    var swipeUpAction: String
        get() = prefs.getString(KEY_SWIPE_UP, ACTION_APP_DRAWER) ?: ACTION_APP_DRAWER
        set(value) = prefs.edit().putString(KEY_SWIPE_UP, value).apply()
    
    var swipeDownAction: String
        get() = prefs.getString(KEY_SWIPE_DOWN, ACTION_NOTIFICATIONS) ?: ACTION_NOTIFICATIONS
        set(value) = prefs.edit().putString(KEY_SWIPE_DOWN, value).apply()
    
    var doubleTapAction: String
        get() = prefs.getString(KEY_DOUBLE_TAP, ACTION_NONE) ?: ACTION_NONE
        set(value) = prefs.edit().putString(KEY_DOUBLE_TAP, value).apply()
    
    var pinchInAction: String
        get() = prefs.getString(KEY_PINCH_IN, ACTION_NONE) ?: ACTION_NONE
        set(value) = prefs.edit().putString(KEY_PINCH_IN, value).apply()
    
    var pinchOutAction: String
        get() = prefs.getString(KEY_PINCH_OUT, ACTION_NONE) ?: ACTION_NONE
        set(value) = prefs.edit().putString(KEY_PINCH_OUT, value).apply()
    
    // Appearance Settings
    var iconPack: String
        get() = prefs.getString(KEY_ICON_PACK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ICON_PACK, value).apply()
    
    var scrollEffect: String
        get() = prefs.getString(KEY_SCROLL_EFFECT, SCROLL_EFFECT_NONE) ?: SCROLL_EFFECT_NONE
        set(value) = prefs.edit().putString(KEY_SCROLL_EFFECT, value).apply()
    
    var animationSpeed: Float
        get() = prefs.getFloat(KEY_ANIMATION_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ANIMATION_SPEED, value).apply()
    
    // Badge Settings
    var showNotificationBadges: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BADGES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BADGES, value).apply()
    
    var showUnreadCount: Boolean
        get() = prefs.getBoolean(KEY_SHOW_UNREAD_COUNT, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_UNREAD_COUNT, value).apply()
    
    companion object {
        // Keys - Made public for backup/restore functionality
        const val KEY_HOME_GRID_COLUMNS = "home_grid_columns"
        const val KEY_HOME_GRID_ROWS = "home_grid_rows"
        const val KEY_ICON_SIZE = "icon_size"
        const val KEY_SHOW_ICON_LABELS = "show_icon_labels"
        const val KEY_DESKTOP_PADDING = "desktop_padding"
        const val KEY_DRAWER_GRID_COLUMNS = "drawer_grid_columns"
        const val KEY_DRAWER_GRID_ROWS = "drawer_grid_rows"
        const val KEY_DRAWER_STYLE = "drawer_style"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_DOCK_ICONS = "dock_icons"
        const val KEY_SCROLLABLE_DOCK = "scrollable_dock"
        const val KEY_FOLDER_STYLE = "folder_style"
        const val KEY_FOLDER_PREVIEW = "folder_preview"
        const val KEY_SWIPE_UP = "swipe_up"
        const val KEY_SWIPE_DOWN = "swipe_down"
        const val KEY_DOUBLE_TAP = "double_tap"
        const val KEY_PINCH_IN = "pinch_in"
        const val KEY_PINCH_OUT = "pinch_out"
        const val KEY_ICON_PACK = "icon_pack"
        const val KEY_SCROLL_EFFECT = "scroll_effect"
        const val KEY_ANIMATION_SPEED = "animation_speed"
        const val KEY_SHOW_BADGES = "show_badges"
        const val KEY_SHOW_UNREAD_COUNT = "show_unread_count"
        
        // Values
        const val DRAWER_STYLE_VERTICAL = "vertical"
        const val DRAWER_STYLE_HORIZONTAL = "horizontal"
        const val DRAWER_STYLE_LIST = "list"
        
        const val FOLDER_STYLE_GRID = "grid"
        const val FOLDER_STYLE_LIST = "list"
        
        const val FOLDER_PREVIEW_GRID = "grid"
        const val FOLDER_PREVIEW_LIST = "list"
        const val FOLDER_PREVIEW_NONE = "none"
        
        const val ACTION_NONE = "none"
        const val ACTION_APP_DRAWER = "app_drawer"
        const val ACTION_SEARCH = "search"
        const val ACTION_NOTIFICATIONS = "notifications"
        const val ACTION_EXPAND_NOTIFICATIONS = "expand_notifications"
        
        const val SCROLL_EFFECT_NONE = "none"
        const val SCROLL_EFFECT_CUBE = "cube"
        const val SCROLL_EFFECT_CYLINDER = "cylinder"
        const val SCROLL_EFFECT_CAROUSEL = "carousel"
    }
}

