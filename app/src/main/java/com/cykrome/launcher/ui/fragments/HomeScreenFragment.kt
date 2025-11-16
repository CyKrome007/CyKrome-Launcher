package com.cykrome.launcher.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.ui.adapters.AppIconAdapter
import com.cykrome.launcher.util.AppLoader
import kotlinx.coroutines.launch

class HomeScreenFragment : Fragment() {
    
    private lateinit var homePager: ViewPager2
    private lateinit var preferences: LauncherPreferences
    private var apps: List<AppInfo> = emptyList()
    
    companion object {
        fun newInstance(): HomeScreenFragment {
            return HomeScreenFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_screen, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            preferences = LauncherPreferences(requireContext())
            homePager = view.findViewById(R.id.homePager)
            
            // Adjust search bar position to account for top padding
            adjustSearchBarPosition(view)
            
            // Set up search bar click
            view.findViewById<View>(R.id.searchBarContainer)?.setOnClickListener {
                openGoogleSearch()
            }
            
            // Set up gesture interceptor for swipe up to open app drawer
            homePager.setOnTouchListener { v, event ->
                // Let the parent handle gestures for app drawer
                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.onTouchEvent(event)
                false // Don't consume the event, let ViewPager2 handle it
            }
            
            // Home screen is empty by default - no need to load apps
            // Just set up the pager with empty list
            if (isAdded && view != null) {
                setupHomePager()
                setupDock(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("HomeScreenFragment", "Error in onViewCreated: ${e.message}", e)
        }
    }
    
    private fun adjustSearchBarPosition(view: View) {
        try {
            val searchBarContainer = view.findViewById<View>(R.id.searchBarContainer)
            val statusBarHeight = getStatusBarHeight()
            val extraPadding = (20 * resources.displayMetrics.density).toInt() // 20dp
            val totalTopPadding = statusBarHeight + extraPadding
            
            // Set margin top to account for status bar and padding
            val layoutParams = searchBarContainer?.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            layoutParams?.topMargin = totalTopPadding
            searchBarContainer?.layoutParams = layoutParams
            
            // Update search bar background based on theme
            if (isDarkMode(requireContext())) {
                searchBarContainer?.setBackgroundResource(R.drawable.search_bar_background_dark)
            } else {
                searchBarContainer?.setBackgroundResource(R.drawable.search_bar_background)
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error adjusting search bar position: ${e.message}", e)
        }
    }
    
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    private fun openGoogleSearch() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", "")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to opening browser
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                startActivity(intent)
            } catch (e2: Exception) {
                android.util.Log.e("HomeScreenFragment", "Error opening search: ${e2.message}", e2)
            }
        }
    }
    
    private fun setupHomePager() {
        try {
            if (!isAdded || view == null) return
            
            // Load home screen items from preferences
            val homeScreenItems = com.cykrome.launcher.util.HomeScreenManager.loadHomeScreenItems(requireContext())
            val appItems = homeScreenItems.filterIsInstance<com.cykrome.launcher.model.HomeScreenItem.AppItem>()
            
            // Convert to AppInfo list by loading app info for each item
            val homeScreenApps = mutableListOf<AppInfo>()
            val packageManager = requireContext().packageManager
            appItems.forEach { item ->
                try {
                    val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                    val activityName = launchIntent?.component?.className ?: item.activityName
                    
                    homeScreenApps.add(AppInfo(
                        packageName = item.packageName,
                        activityName = activityName,
                        label = label,
                        icon = icon,
                        applicationInfo = appInfo
                    ))
                } catch (e: Exception) {
                    android.util.Log.w("HomeScreenFragment", "Could not load app ${item.packageName}: ${e.message}")
                }
            }
            
            // Group apps by page and ensure we have at least one page
            // Allow users to have multiple empty pages - minimum 1, but can add more
            val maxPage = appItems.maxOfOrNull { it.page } ?: 0
            // Get saved page count from preferences, default to maxPage + 1 or 1
            val savedPageCount = preferences.getInt("home_screen_page_count", -1)
            val totalPages = if (savedPageCount > 0) savedPageCount else (maxPage + 1).coerceAtLeast(1)
            
            val adapter = DesktopPageAdapter(this, homeScreenApps, appItems, preferences, totalPages)
            homePager.adapter = adapter
            
            // Show page indicator - always show if there are multiple pages
            val pageIndicator = view?.findViewById<com.google.android.material.tabs.TabLayout>(R.id.pageIndicator)
            val pageCount = adapter.itemCount
            if (pageIndicator != null) {
                if (pageCount > 1) {
                    pageIndicator.visibility = View.VISIBLE
                    // Clear existing tabs and mediator
                    pageIndicator.removeAllTabs()
                    // Remove any existing mediator
                    try {
                        val mediatorField = pageIndicator.javaClass.getDeclaredField("mediator")
                        mediatorField.isAccessible = true
                        val mediator = mediatorField.get(pageIndicator)
                        mediator?.javaClass?.getDeclaredMethod("detach")?.invoke(mediator)
                    } catch (e: Exception) {
                        // Ignore if no mediator exists
                    }
                    // Add tabs for each page
                    for (i in 0 until pageCount) {
                        pageIndicator.addTab(pageIndicator.newTab())
                    }
                    // Attach mediator to sync with ViewPager2
                    com.google.android.material.tabs.TabLayoutMediator(pageIndicator, homePager) { _, _ -> }.attach()
                } else {
                    pageIndicator.visibility = View.GONE
                }
            }
            
            // Enable/disable user input based on page count
            // If only one page, disable horizontal scrolling to allow vertical scrolling in RecyclerView
            homePager.isUserInputEnabled = pageCount > 1
            
            // ViewPager2 is horizontal by default - keep it horizontal for desktop pages
            // Desktop pages scroll horizontally (left/right), apps within pages scroll vertically
            
            // Set page transformer for scroll effects (without stretch/scale effects)
            when (preferences.scrollEffect) {
                LauncherPreferences.SCROLL_EFFECT_CUBE -> {
                    homePager.setPageTransformer { page, position ->
                        page.rotationY = -position * 90
                        // No scaling to prevent stretch effect
                    }
                }
                LauncherPreferences.SCROLL_EFFECT_CYLINDER -> {
                    homePager.setPageTransformer { page, position ->
                        page.rotationY = -position * 45
                        // No scaling to prevent stretch effect
                    }
                }
                LauncherPreferences.SCROLL_EFFECT_CAROUSEL -> {
                    homePager.setPageTransformer { page, position ->
                        val absPosition = Math.abs(position)
                        page.alpha = 1 - absPosition
                        // No scaling to prevent stretch effect
                    }
                }
                else -> {
                    // Default: no page transformer (no effects)
                    homePager.setPageTransformer(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("HomeScreenFragment", "Error setting up pager: ${e.message}", e)
        }
    }
    
    fun refreshApps() {
        // Refresh home screen - reload from preferences
        val currentView = view
        if (isAdded && currentView != null) {
            setupHomePager()
            setupDock(currentView)
        }
    }
    
    private fun setupDock(view: View) {
        try {
            // Find the dock containers - dockScrollView is the outer LinearLayout, dockContainer is the inner one
            val dockScrollView = view.findViewById<ViewGroup>(R.id.dockScrollView)
            val dockContainer = dockScrollView?.findViewById<ViewGroup>(R.id.dockContainer) // Get the LinearLayout inside
            
            if (dockContainer == null || dockScrollView == null) {
                android.util.Log.e("HomeScreenFragment", "Dock container not found - scrollView: ${dockScrollView != null}, container: ${dockContainer != null}")
                return
            }
            
            // Update dock background based on theme
            if (isDarkMode(requireContext())) {
                dockScrollView?.setBackgroundResource(R.drawable.dock_background_dark)
            } else {
                dockScrollView?.setBackgroundResource(R.drawable.dock_background)
            }
            
            // Clear existing dock items
            dockContainer.removeAllViews()
            
            // Define the 5 apps that should always be in the dock
            val dockAppPackages = listOf(
                "com.android.dialer", // Phone
                "com.android.mms", // Message
                "com.android.camera2", // Camera
                "com.android.contacts", // Contacts
                "com.android.chrome" // Chrome
            )
            
            // Alternative package names in case the above don't match
            val dockAppPackageAlternatives = mapOf(
                "com.android.dialer" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
                "com.android.mms" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
                "com.android.camera2" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.samsung.android.camera"),
                "com.android.contacts" to listOf("com.google.android.contacts", "com.samsung.android.contacts"),
                "com.android.chrome" to listOf("com.chrome.browser", "com.chrome.dev")
            )
            
            // Load all apps asynchronously
            lifecycleScope.launch {
                try {
                    val allApps = AppLoader.loadApps(requireContext())
                    
                    // Find and add dock apps
                    dockAppPackages.forEach { packageName ->
                        var app = allApps.find { it.packageName == packageName }
                        
                        // Try alternatives if not found
                        if (app == null) {
                            val alternatives = dockAppPackageAlternatives[packageName] ?: emptyList()
                            for (altPackage in alternatives) {
                                app = allApps.find { it.packageName == altPackage }
                                if (app != null) break
                            }
                        }
                        
                        if (app != null) {
                            // Create dock item view on main thread
                            requireActivity().runOnUiThread {
                                val dockItem = createDockItemView(app)
                                dockContainer.addView(dockItem)
                            }
                        } else {
                            android.util.Log.w("HomeScreenFragment", "Dock app not found: $packageName")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenFragment", "Error loading apps for dock: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up dock: ${e.message}", e)
        }
    }
    
    private fun createDockItemView(app: AppInfo): View {
        val context = requireContext()
        
        // Create a custom dock item view with background
        val dockItem = android.widget.FrameLayout(context)
        dockItem.layoutParams = android.view.ViewGroup.LayoutParams(
            (64 * resources.displayMetrics.density).toInt(), // 64dp width
            (64 * resources.displayMetrics.density).toInt() // 64dp height
        )
        
        // Add background with rounded corners
        val backgroundDrawable = if (isDarkMode(context)) {
            context.getDrawable(R.drawable.app_icon_background_dark)
        } else {
            context.getDrawable(R.drawable.app_icon_background)
        }
        dockItem.background = backgroundDrawable
        
        // Add padding
        val padding = (8 * resources.displayMetrics.density).toInt()
        dockItem.setPadding(padding, padding, padding, padding)
        
        // Add elevation
        dockItem.elevation = 4f
        
        // Add click effect
        dockItem.foreground = context.getDrawable(android.R.attr.selectableItemBackground)
        dockItem.isClickable = true
        dockItem.isFocusable = true
        
        // Create icon view
        val iconView = android.widget.ImageView(context)
        val iconSize = (48 * resources.displayMetrics.density).toInt() // 48dp icon
        iconView.layoutParams = android.view.ViewGroup.LayoutParams(
            iconSize,
            iconSize
        )
        iconView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        iconView.adjustViewBounds = true
        
        // Set app icon
        val packageManager = context.packageManager
        try {
            val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
            iconView.setImageDrawable(packageManager.getApplicationIcon(appInfo))
        } catch (e: Exception) {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        dockItem.addView(iconView)
        
        // Set click listener to launch app
        dockItem.setOnClickListener {
            launchApp(app)
        }
        
        // Add margin between dock items
        val margin = (8 * resources.displayMetrics.density).toInt()
        val params = android.view.ViewGroup.MarginLayoutParams(dockItem.layoutParams)
        params.setMargins(margin, 0, margin, 0)
        dockItem.layoutParams = params
        
        return dockItem
    }
    
    private fun isDarkMode(context: android.content.Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun launchApp(app: AppInfo) {
        try {
            val packageManager = requireContext().packageManager
            val intent = app.getLaunchIntent(packageManager)
            intent?.let {
                startActivity(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error launching app: ${e.message}", e)
        }
    }
    
    fun addAppToHomeScreen(packageName: String, activityName: String) {
        // Find first available position on home screen
        val gridColumns = preferences.homeGridColumns
        val gridRows = preferences.homeGridRows
        
        // Load existing items
        val existingItems = com.cykrome.launcher.util.HomeScreenManager.loadHomeScreenItems(requireContext())
        val appItems = existingItems.filterIsInstance<com.cykrome.launcher.model.HomeScreenItem.AppItem>()
        
        // Get current page count
        val currentPageCount = preferences.getInt("home_screen_page_count", -1)
        val maxPage = appItems.maxOfOrNull { it.page } ?: 0
        val totalPages = if (currentPageCount > 0) currentPageCount else (maxPage + 1).coerceAtLeast(1)
        
        // Try to find available position, starting from page 0
        var found = false
        var cellX = 0
        var cellY = 0
        var page = 0
        
        // Search through all pages
        for (currentPage in 0 until totalPages) {
            for (y in 0 until gridRows) {
                for (x in 0 until gridColumns) {
                    val occupied = appItems.any { it.cellX == x && it.cellY == y && it.page == currentPage }
                    if (!occupied) {
                        cellX = x
                        cellY = y
                        page = currentPage
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (found) break
        }
        
        // If all pages are full, create a new page
        if (!found) {
            page = totalPages
            cellX = 0
            cellY = 0
            // Update page count
            preferences.putInt("home_screen_page_count", totalPages + 1)
            found = true
        }
        
        if (found) {
            addAppToHomeScreenAtPosition(packageName, activityName, cellX, cellY, page)
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                "Error adding app to home screen",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    fun addAppToHomeScreenAtPosition(packageName: String, activityName: String, cellX: Int, cellY: Int, page: Int) {
        try {
            // Load existing home screen items
            val existingItems = com.cykrome.launcher.util.HomeScreenManager.loadHomeScreenItems(requireContext())
            
            // Check if app already exists at this position
            val existingAtPosition = existingItems.filterIsInstance<com.cykrome.launcher.model.HomeScreenItem.AppItem>()
                .firstOrNull { it.cellX == cellX && it.cellY == cellY && it.page == page }
            
            if (existingAtPosition != null) {
                // Remove existing item at this position
                val updatedItems = existingItems.toMutableList()
                updatedItems.remove(existingAtPosition)
                com.cykrome.launcher.util.HomeScreenManager.saveHomeScreenItems(requireContext(), updatedItems)
            }
            
            // Create new app item
            val newItem = com.cykrome.launcher.model.HomeScreenItem.AppItem(
                packageName = packageName,
                activityName = activityName,
                cellX = cellX,
                cellY = cellY,
                page = page
            )
            
            // Add to list
            val allItems = com.cykrome.launcher.util.HomeScreenManager.loadHomeScreenItems(requireContext())
            val finalItems = allItems.toMutableList()
            finalItems.add(newItem)
            
            // Save
            com.cykrome.launcher.util.HomeScreenManager.saveHomeScreenItems(requireContext(), finalItems)
            
            // Update page count if needed
            val maxPageAfterAdd = finalItems.filterIsInstance<com.cykrome.launcher.model.HomeScreenItem.AppItem>()
                .maxOfOrNull { it.page } ?: 0
            val newPageCount = (maxPageAfterAdd + 1).coerceAtLeast(1)
            preferences.putInt("home_screen_page_count", newPageCount)
            
            // Get app label for toast
            val packageManager = requireContext().packageManager
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            
            // Refresh home screen
            refreshApps()
            
            android.widget.Toast.makeText(
                requireContext(),
                "Added $appLabel to home screen",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error adding app to home screen: ${e.message}", e)
        }
    }
}

class DesktopPageFragment : Fragment() {
    
    private lateinit var apps: List<AppInfo>
    private lateinit var appItems: List<com.cykrome.launcher.model.HomeScreenItem.AppItem>
    private lateinit var preferences: LauncherPreferences
    private var swipeStartY = 0f
    
    companion object {
        fun newInstance(apps: List<AppInfo>, appItems: List<com.cykrome.launcher.model.HomeScreenItem.AppItem>, preferences: LauncherPreferences): DesktopPageFragment {
            val fragment = DesktopPageFragment()
            fragment.apps = apps
            fragment.appItems = appItems
            fragment.preferences = preferences
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.desktop_page, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.desktopGrid)
            val columns = preferences.homeGridColumns
            val layoutManager = GridLayoutManager(requireContext(), columns)
            recyclerView.layoutManager = layoutManager
            
            // Ensure RecyclerView can scroll vertically
            recyclerView.isNestedScrollingEnabled = true
            
            // Add top padding to RecyclerView to account for search bar and status bar
            val statusBarHeight = getStatusBarHeightForPage()
            val searchBarHeight = (60 * resources.displayMetrics.density).toInt() // Approximate search bar height
            val extraPadding = (16 * resources.displayMetrics.density).toInt() // 16dp
            val totalTopPadding = statusBarHeight + searchBarHeight + extraPadding
            
            recyclerView.setPadding(
                recyclerView.paddingLeft,
                totalTopPadding,
                recyclerView.paddingRight,
                recyclerView.paddingBottom
            )
            
            // Set up drag and drop listener for apps from drawer
            setupDragAndDropFromDrawer(recyclerView)
            
            // Set up gesture interceptor for swipe up to open app drawer
            // Use a custom touch listener that checks for swipe up gestures
            recyclerView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        swipeStartY = event.y
                        false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - swipeStartY
                        // If swiping up and RecyclerView is at the top, let parent handle it
                        if (deltaY < -50 && !recyclerView.canScrollVertically(-1)) {
                            // RecyclerView is at top and user is swiping up - let parent handle
                            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.onTouchEvent(event)
                            true // Consume the event
                        } else {
                            false // Let RecyclerView handle scrolling
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaY = event.y - swipeStartY
                        // If it was a swipe up and RecyclerView was at top, trigger app drawer
                        if (deltaY < -100 && !recyclerView.canScrollVertically(-1)) {
                            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.openAppDrawer()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            val adapter = AppIconAdapter(apps.toMutableList(), preferences, requireContext())
            recyclerView.adapter = adapter
            
            // Set up drag and drop for rearranging apps
            setupDragAndDrop(recyclerView, adapter)
        } catch (e: Exception) {
            android.util.Log.e("DesktopPageFragment", "Error in onViewCreated: ${e.message}", e)
        }
    }
    
    private fun getStatusBarHeightForPage(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    private fun setupDragAndDropFromDrawer(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        recyclerView.setOnDragListener { view, dragEvent ->
            when (dragEvent.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> {
                    // Allow drop
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    // Highlight drop zone
                    view.alpha = 0.8f
                    true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    // Remove highlight
                    view.alpha = 1f
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    // Handle drop
                    val clipData = dragEvent.clipData
                    val item = clipData.getItemAt(0)
                    val appData = item.text.toString()
                    
                    // Parse app info from clip data
                    val parts = appData.split("|")
                    if (parts.size >= 2) {
                        val packageName = parts[0]
                        val activityName = parts[1]
                        
                        // Get drop position
                        val x = dragEvent.x
                        val y = dragEvent.y
                        
                        // Calculate grid position
                        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
                        val columns = layoutManager?.spanCount ?: preferences.homeGridColumns
                        val itemWidth = recyclerView.width / columns
                        val itemHeight = itemWidth // Assuming square items
                        
                        // Account for padding
                        val adjustedX = x - recyclerView.paddingLeft
                        val adjustedY = y - recyclerView.paddingTop
                        
                        val cellX = (adjustedX / itemWidth).toInt().coerceIn(0, columns - 1)
                        val cellY = (adjustedY / itemHeight).toInt().coerceAtLeast(0)
                        
                        // Get current page from ViewPager2
                        val homeScreenFragment = parentFragment as? HomeScreenFragment
                        val viewPager = homeScreenFragment?.view?.findViewById<ViewPager2>(R.id.homePager)
                        val currentPage = viewPager?.currentItem ?: 0
                        
                        // Add to home screen using package name and activity name
                        // Call parent HomeScreenFragment's method
                        homeScreenFragment?.addAppToHomeScreenAtPosition(packageName, activityName, cellX, cellY, currentPage)
                    }
                    
                    // Remove highlight
                    view.alpha = 1f
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    // Remove highlight
                    view.alpha = 1f
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupDragAndDrop(recyclerView: androidx.recyclerview.widget.RecyclerView, adapter: AppIconAdapter) {
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN or
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION || 
                    toPosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    return false
                }
                // Move item in adapter
                adapter.moveItem(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                // Not used for drag and drop
            }
            
            override fun onSelectedChanged(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Close any open popup menu when drag starts
                    adapter.closeMenu()
                    viewHolder?.itemView?.alpha = 0.5f
                    viewHolder?.itemView?.scaleX = 1.1f
                    viewHolder?.itemView?.scaleY = 1.1f
                }
            }
            
            override fun clearView(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1f
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    
}

class DesktopPageAdapter(
    fragment: Fragment,
    private val allApps: List<AppInfo>,
    private val appItems: List<com.cykrome.launcher.model.HomeScreenItem.AppItem>,
    private val preferences: LauncherPreferences,
    private val totalPages: Int = 1
) : FragmentStateAdapter(fragment) {
    
    private val itemsPerPage = preferences.homeGridColumns * preferences.homeGridRows
    
    override fun getItemCount(): Int {
        // Return the total number of pages (at least 1)
        return totalPages.coerceAtLeast(1)
    }
    
    override fun createFragment(position: Int): Fragment {
        // Get apps for this specific page
        val pageApps = appItems
            .filter { it.page == position }
            .mapNotNull { item ->
                // Find the corresponding AppInfo
                allApps.find { it.packageName == item.packageName && it.activityName == item.activityName }
            }
        
        return DesktopPageFragment.newInstance(pageApps, appItems.filter { it.page == position }, preferences)
    }
}

