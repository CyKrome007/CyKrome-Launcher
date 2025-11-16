package com.cykrome.launcher.ui.adapters

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.util.AppLoader
import com.cykrome.launcher.util.BadgeHelper

class AppIconAdapter(
    private val apps: List<AppInfo>,
    private val preferences: LauncherPreferences,
    private val context: android.content.Context
) : RecyclerView.Adapter<AppIconAdapter.AppIconViewHolder>() {
    
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppIconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return AppIconViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AppIconViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class AppIconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appLabel: TextView = itemView.findViewById(R.id.appLabel)
        private val badgeCount: TextView = itemView.findViewById(R.id.badgeCount)
        
        fun bind(app: AppInfo) {
            // Set icon
            val icon = if (preferences.iconPack.isNotEmpty()) {
                AppLoader.getIconPackIcon(context, preferences.iconPack, app) ?: app.icon
            } else {
                app.icon
            }
            
            appIcon.setImageDrawable(icon)
            
            // Resize icon based on preferences
            val iconSize = preferences.iconSize
            val layoutParams = appIcon.layoutParams
            layoutParams.width = iconSize
            layoutParams.height = iconSize
            appIcon.layoutParams = layoutParams
            
            // Set label
            if (preferences.showIconLabels) {
                appLabel.text = app.label
                appLabel.visibility = View.VISIBLE
            } else {
                appLabel.visibility = View.GONE
            }
            
            // Set badge
            if (preferences.showNotificationBadges) {
                val count = BadgeHelper.getBadgeCount(app.packageName)
                if (count > 0) {
                    badgeCount.text = if (count > 99) "99+" else count.toString()
                    badgeCount.visibility = View.VISIBLE
                } else {
                    badgeCount.visibility = View.GONE
                }
            } else {
                badgeCount.visibility = View.GONE
            }
            
            // Set click listeners
            itemView.setOnClickListener {
                onAppClick?.invoke(app)
                launchApp(app)
            }
            
            itemView.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }
        }
        
        private fun launchApp(app: AppInfo) {
            val packageManager = context.packageManager
            val intent = app.getLaunchIntent(packageManager)
            intent?.let {
                context.startActivity(it)
            }
        }
    }
}

