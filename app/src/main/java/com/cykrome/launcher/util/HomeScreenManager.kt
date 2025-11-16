package com.cykrome.launcher.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.cykrome.launcher.model.HomeScreenItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HomeScreenManager {
    private const val KEY_HOME_SCREEN_ITEMS = "home_screen_items"
    
    fun saveHomeScreenItems(context: Context, items: List<HomeScreenItem>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gson = Gson()
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_HOME_SCREEN_ITEMS, json).apply()
    }
    
    fun loadHomeScreenItems(context: Context): List<HomeScreenItem> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(KEY_HOME_SCREEN_ITEMS, null) ?: return emptyList()
        val gson = Gson()
        val type = object : TypeToken<List<HomeScreenItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenManager", "Error loading home screen items: ${e.message}", e)
            emptyList()
        }
    }
}

