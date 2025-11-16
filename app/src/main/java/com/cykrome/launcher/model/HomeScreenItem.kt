package com.cykrome.launcher.model

sealed class HomeScreenItem {
    data class AppItem(
        val packageName: String,
        val activityName: String,
        val cellX: Int,
        val cellY: Int,
        val page: Int
    ) : HomeScreenItem()
    
    data class WidgetItem(
        val appWidgetId: Int,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val page: Int
    ) : HomeScreenItem()
}

