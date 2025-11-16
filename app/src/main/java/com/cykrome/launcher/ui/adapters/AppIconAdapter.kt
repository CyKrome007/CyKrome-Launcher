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
    var apps: MutableList<AppInfo>,
    private val preferences: LauncherPreferences,
    private val context: android.content.Context
) : RecyclerView.Adapter<AppIconAdapter.AppIconViewHolder>() {
    
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    var onAddToHomeScreen: ((AppInfo) -> Unit)? = null
    private var currentPopupMenu: androidx.appcompat.widget.PopupMenu? = null
    
    fun closeMenu() {
        currentPopupMenu?.dismiss()
        currentPopupMenu = null
    }
    
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                apps[i] = apps[i + 1].also { apps[i + 1] = apps[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                apps[i] = apps[i - 1].also { apps[i - 1] = apps[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
    
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
        
        private var longPressStartX = 0f
        private var longPressStartY = 0f
        private var isLongPressed = false
        private var longPressStartTime = 0L
        private var hasShownMenu = false
        private val dragThreshold = 50f * context.resources.displayMetrics.density // 50dp threshold
        
        fun bind(app: AppInfo) {
            // Set icon
            val icon = if (preferences.iconPack.isNotEmpty()) {
                AppLoader.getIconPackIcon(context, preferences.iconPack, app) ?: app.icon
            } else {
                app.icon
            }
            
            appIcon.setImageDrawable(icon)
            
            // Resize icon based on preferences (convert percentage to dp)
            val iconSizePercent = preferences.iconSize
            val baseSize = 56 // Base size in dp
            val iconSizeDp = (baseSize * iconSizePercent / 100).coerceAtLeast(32).coerceAtMost(96)
            val iconSizePx = (iconSizeDp * context.resources.displayMetrics.density).toInt()
            val layoutParams = appIcon.layoutParams
            layoutParams.width = iconSizePx
            layoutParams.height = iconSizePx
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
            
            // Use a single touch listener to handle both long press and drag
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        longPressStartX = event.x
                        longPressStartY = event.y
                        longPressStartTime = System.currentTimeMillis()
                        hasShownMenu = false
                        isLongPressed = false
                        false // Let the event propagate to allow long press detection
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (hasShownMenu || isLongPressed) {
                            // Menu is shown, check if user is dragging
                            val currentX = event.rawX
                            val currentY = event.rawY
                            
                            val location = IntArray(2)
                            v.getLocationOnScreen(location)
                            val initialX = location[0] + longPressStartX
                            val initialY = location[1] + longPressStartY
                            
                            val deltaX = Math.abs(currentX - initialX)
                            val deltaY = Math.abs(currentY - initialY)
                            val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                            
                            // If dragged beyond threshold, start drag operation
                            if (distance > dragThreshold) {
                                isLongPressed = false
                                hasShownMenu = false
                                // Close menu
                                this@AppIconAdapter.closeMenu()
                                // Notify parent to start drag (this will close drawer)
                                onAppLongClick?.invoke(app)
                                // Start drag
                                val dragShadowBuilder = android.view.View.DragShadowBuilder(v)
                                val dragData = android.content.ClipData.newPlainText("app", "${app.packageName}|${app.activityName}")
                                v.startDragAndDrop(dragData, dragShadowBuilder, app, 0)
                                true
                            } else {
                                // Still within threshold, consume event to prevent scrolling
                                true
                            }
                        } else {
                            // Check if enough time has passed for long press
                            val elapsedTime = System.currentTimeMillis() - longPressStartTime
                            if (elapsedTime >= android.view.ViewConfiguration.getLongPressTimeout()) {
                                // Long press detected, show menu
                                showAppMenu(app)
                                hasShownMenu = true
                                isLongPressed = true
                                true // Consume the event
                            } else {
                                false // Not long press yet, let event propagate
                            }
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (hasShownMenu && !isLongPressed) {
                            // Menu was shown but user didn't drag, just release
                            // Menu will stay open until user selects an option
                            true
                        } else if (!hasShownMenu) {
                            // No menu was shown, let click handler work
                            false
                        } else {
                            // Menu is shown, consume to prevent click
                            true
                        }
                    }
                    else -> false
                }
            }
            
            // Keep the click listener but make sure it doesn't fire when menu is shown
            itemView.setOnClickListener {
                // Only handle click if menu is not shown
                if (!hasShownMenu && !isLongPressed) {
                    onAppClick?.invoke(app)
                    launchApp(app)
                }
            }
        }
        
        private fun showAppMenu(app: AppInfo) {
            val context = itemView.context
            val popupMenu = androidx.appcompat.widget.PopupMenu(context, itemView)
            val menu = popupMenu.menu
            
            // Check if the item is on the right side of the screen
            // If so, show menu on the left side to prevent it from going off screen
            val location = IntArray(2)
            itemView.getLocationOnScreen(location)
            val screenWidth = context.resources.displayMetrics.widthPixels
            val itemRight = location[0] + itemView.width
            val itemLeft = location[0]
            val itemCenter = location[0] + itemView.width / 2
            val isOnRightSide = itemCenter > screenWidth * 0.5 // If item center is in the right 50% of screen
            
            // Adjust menu gravity based on position
            // In LTR: START = left of anchor, END = right of anchor
            if (isOnRightSide) {
                // Show menu on the left side of the anchor (to prevent going off screen)
                popupMenu.gravity = android.view.Gravity.START
                
                // Calculate offset to ensure menu stays on screen
                try {
                    val popup = popupMenu.javaClass.getDeclaredField("mPopup")
                    popup.isAccessible = true
                    val menuPopupHelper = popup.get(popupMenu)
                    // Estimate menu width (approximately 200dp)
                    val menuWidth = (200 * context.resources.displayMetrics.density).toInt()
                    // Calculate offset: move menu left by enough to keep it on screen
                    // Position menu so its right edge aligns with item's left edge, then move left a bit more
                    val offsetX = -(itemRight - screenWidth + menuWidth + (32 * context.resources.displayMetrics.density).toInt())
                    menuPopupHelper?.javaClass?.getDeclaredMethod("setHorizontalOffset", Int::class.java)?.invoke(menuPopupHelper, offsetX.coerceAtMost(-(16 * context.resources.displayMetrics.density).toInt()))
                } catch (e: Exception) {
                    // Reflection failed, try alternative approach - use gravity only
                    android.util.Log.d("AppIconAdapter", "Could not set menu offset: ${e.message}")
                }
            } else {
                // Default: show menu on the right side of the anchor
                popupMenu.gravity = android.view.Gravity.END
            }
            
            // Load app shortcuts
            val shortcuts = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                com.cykrome.launcher.util.ShortcutHelper.getShortcuts(context, app.packageName)
            } else {
                emptyList()
            }
            
            // Add shortcuts to menu first (if any)
            shortcuts.forEachIndexed { index, shortcut ->
                val shortcutTitle = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: "Shortcut"
                val menuItem = menu.add(0, android.view.Menu.NONE, index, shortcutTitle)
                
                // Set icon if available
                val icon = com.cykrome.launcher.util.ShortcutHelper.getShortcutIcon(context, shortcut, app.packageName)
                if (icon != null) {
                    menuItem.icon = icon
                }
                
                // Store shortcut info in menu item
                menuItem.intent = android.content.Intent().putExtra("shortcut_id", shortcut.id)
            }
            
            // Add separator if there are shortcuts
            if (shortcuts.isNotEmpty()) {
                menu.add(android.view.Menu.NONE, android.view.Menu.NONE, shortcuts.size, "")
                    .setEnabled(false)
            }
            
            // Add standard menu items
            popupMenu.menuInflater.inflate(R.menu.app_context_menu, menu)
            
            // Store reference to close it later
            this@AppIconAdapter.currentPopupMenu = popupMenu
            
            // Prevent auto-dismiss on outside touch
            // We'll manually control when it closes
            try {
                val popup = popupMenu.javaClass.getDeclaredField("mPopup")
                popup.isAccessible = true
                val menuPopupHelper = popup.get(popupMenu)
                menuPopupHelper?.javaClass?.getDeclaredMethod("setForceShowIcon", Boolean::class.java)?.invoke(menuPopupHelper, true)
                val dismissOnOutsideTouch = menuPopupHelper?.javaClass?.getDeclaredMethod("setOutsideTouchable", Boolean::class.java)
                // Keep it touchable but we'll handle dismissal manually
            } catch (e: Exception) {
                // Fallback if reflection fails
                android.util.Log.d("AppIconAdapter", "Could not prevent auto-dismiss: ${e.message}")
            }
            
            popupMenu.setOnMenuItemClickListener { item ->
                // Check if this is a shortcut
                val shortcutId = item.intent?.getStringExtra("shortcut_id")
                if (shortcutId != null) {
                    // Launch shortcut
                    val shortcut = shortcuts.find { it.id == shortcutId }
                    if (shortcut != null) {
                        com.cykrome.launcher.util.ShortcutHelper.launchShortcut(context, shortcut, app.packageName)
                        popupMenu.dismiss()
                        return@setOnMenuItemClickListener true
                    }
                }
                
                // Handle standard menu items
                when (item.itemId) {
                    R.id.menu_uninstall -> {
                        uninstallApp(app)
                        popupMenu.dismiss()
                        // Hide blur overlay
                        (context as? android.app.Activity)?.let { activity ->
                            if (activity is com.cykrome.launcher.ui.LauncherActivity) {
                                activity.hideBlurOverlay()
                            }
                        }
                        true
                    }
                    R.id.menu_app_info -> {
                        showAppInfo(app)
                        popupMenu.dismiss()
                        // Hide blur overlay
                        (context as? android.app.Activity)?.let { activity ->
                            if (activity is com.cykrome.launcher.ui.LauncherActivity) {
                                activity.hideBlurOverlay()
                            }
                        }
                        true
                    }
                    R.id.menu_add_to_home -> {
                        // Add app to home screen
                        addAppToHomeScreen(app)
                        popupMenu.dismiss()
                        // Hide blur overlay
                        (context as? android.app.Activity)?.let { activity ->
                            if (activity is com.cykrome.launcher.ui.LauncherActivity) {
                                activity.hideBlurOverlay()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.setOnDismissListener {
                this@AppIconAdapter.currentPopupMenu = null
                // Hide blur overlay when menu is dismissed
                (context as? android.app.Activity)?.let { activity ->
                    if (activity is com.cykrome.launcher.ui.LauncherActivity) {
                        activity.hideBlurOverlay()
                    }
                }
            }
            
            // Show blur overlay before showing menu
            (context as? android.app.Activity)?.let { activity ->
                if (activity is com.cykrome.launcher.ui.LauncherActivity) {
                    activity.showBlurOverlay()
                }
            }
            
            popupMenu.show()
        }
        
        private fun uninstallApp(app: AppInfo) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.parse("package:${app.packageName}")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("AppIconAdapter", "Error uninstalling app: ${e.message}", e)
            }
        }
        
        private fun showAppInfo(app: AppInfo) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${app.packageName}")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("AppIconAdapter", "Error showing app info: ${e.message}", e)
            }
        }
        
        private fun addAppToHomeScreen(app: AppInfo) {
            // Notify parent to add app to home screen
            onAddToHomeScreen?.invoke(app)
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

