package com.cykrome.launcher.util

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BadgeHelper {
    private val badgeCounts = mutableMapOf<String, Int>()
    private val badgeCountsLiveData = MutableLiveData<Map<String, Int>>()
    
    val badgeCountsLive: LiveData<Map<String, Int>> = badgeCountsLiveData
    
    fun updateBadgeCount(packageName: String, count: Int) {
        badgeCounts[packageName] = count
        badgeCountsLiveData.postValue(badgeCounts.toMap())
    }
    
    fun getBadgeCount(packageName: String): Int {
        return badgeCounts[packageName] ?: 0
    }
    
    fun clearBadge(packageName: String) {
        badgeCounts.remove(packageName)
        badgeCountsLiveData.postValue(badgeCounts.toMap())
    }
    
    fun clearAllBadges() {
        badgeCounts.clear()
        badgeCountsLiveData.postValue(emptyMap())
    }
}

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val count = activeNotifications.count { n -> n.packageName == packageName }
            BadgeHelper.updateBadgeCount(packageName, count)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val packageName = it.packageName
            val count = activeNotifications.count { n -> n.packageName == packageName }
            BadgeHelper.updateBadgeCount(packageName, count)
        }
    }
}

