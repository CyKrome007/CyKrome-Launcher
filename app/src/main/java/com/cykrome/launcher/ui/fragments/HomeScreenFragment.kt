package com.cykrome.launcher.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
    private var dockContainer: View? = null
    private var pageIndicator: com.google.android.material.tabs.TabLayout? = null
    private var customizationOverlay: View? = null
    private var editModePageIndicator: com.google.android.material.tabs.TabLayout? = null
    var isCustomizationMode = false
    
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
            
            // Set up combined gesture interceptor for swipe down, long press, and other gestures
            setupCombinedTouchListener()
            
            // Home screen is empty by default - no need to load apps
            // Just set up the pager with empty list
            if (isAdded && view != null) {
                setupHomePager()
                setupDock(view)
                setupDockSearch(view)
                setupCustomizationOverlay(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("HomeScreenFragment", "Error in onViewCreated: ${e.message}", e)
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
            
            // Hide dock on Cards page (position 0), show on home screen pages
            val dockContainer = view?.findViewById<View>(R.id.dockContainer)
            
            // Set initial page to 1 (first home screen page, Cards is at 0)
            // Use post to ensure ViewPager2 is fully initialized
            homePager.post {
                if (adapter.itemCount > 1) {
                    homePager.setCurrentItem(1, false)
                    // Ensure dock is visible on home screen (position 1)
                    dockContainer?.visibility = View.VISIBLE
                }
            }
            
            // Show page indicator - always show (even for 1 page) above the dock
            pageIndicator = view?.findViewById<com.google.android.material.tabs.TabLayout>(R.id.pageIndicator)
            val pageCount = adapter.itemCount
            val homeScreenPageCount = pageCount - 1 // Exclude Cards page (position 0 is Cards, 1+ are home screens)
            
            pageIndicator?.let { indicator ->
                // Initially show/hide based on current page (hide on Cards page)
                val initialPage = homePager.currentItem
                indicator.visibility = if (initialPage == 0) View.GONE else View.VISIBLE
                
                // Clear existing tabs and mediator
                indicator.removeAllTabs()
                // Remove any existing mediator
                try {
                    val mediatorField = indicator.javaClass.getDeclaredField("mediator")
                    mediatorField.isAccessible = true
                    val mediator = mediatorField.get(indicator)
                    mediator?.javaClass?.getDeclaredMethod("detach")?.invoke(mediator)
                } catch (e: Exception) {
                    // Ignore if no mediator exists
                }
                // Add tabs for each home screen page (excluding Cards)
                for (i in 0 until homeScreenPageCount) {
                    indicator.addTab(indicator.newTab())
                }
                
                // Post to ensure tabs are laid out, then fix their dimensions to maintain circular shape
                indicator.post {
                    for (i in 0 until indicator.tabCount) {
                        val tab = indicator.getTabAt(i)
                        tab?.let {
                            try {
                                val tabView = it.view
                                val density = resources.displayMetrics.density
                                val size = (10 * density).toInt()
                                // Set fixed dimensions to maintain circular shape
                                tabView.minimumHeight = size
                                tabView.minimumWidth = size
                                // Force layout params to maintain square shape
                                val layoutParams = tabView.layoutParams
                                if (layoutParams != null) {
                                    layoutParams.height = size
                                    layoutParams.width = size
                                    tabView.layoutParams = layoutParams
                                } else {
                                    // Create new layout params if null
                                    val newParams = android.view.ViewGroup.LayoutParams(size, size)
                                    tabView.layoutParams = newParams
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HomeScreenFragment", "Error setting tab dimensions: ${e.message}")
                            }
                        }
                    }
                }
                
                // Set initial tab selection
                if (initialPage > 0 && initialPage - 1 < homeScreenPageCount) {
                    indicator.getTabAt(initialPage - 1)?.select()
                }
            }
            
            // Register page change callback to hide/show dock, page indicator, and update tab selection
            homePager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Hide dock on Cards page (position 0), show on home screen pages
                    dockContainer?.visibility = if (position == 0) View.GONE else View.VISIBLE
                    // Hide page indicator on Cards page (position 0), show on home screen pages
                    if (!isCustomizationMode) {
                        pageIndicator?.visibility = if (position == 0) View.GONE else View.VISIBLE
                    }
                    // Update tab selection for home screen pages only (map position 1 -> tab 0, position 2 -> tab 1, etc.)
                    if (position > 0 && position - 1 < homeScreenPageCount) {
                        pageIndicator?.getTabAt(position - 1)?.select()
                        // Also update edit mode indicator
                        if (isCustomizationMode && position - 1 < homeScreenPageCount) {
                            editModePageIndicator?.getTabAt(position - 1)?.select()
                        }
                    }
                }
            })
            
            // Enable/disable user input based on page count (including Cards page)
            // If only Cards page, disable horizontal scrolling to allow vertical scrolling in RecyclerView
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
            // Update edit mode indicators if in customization mode
            if (isCustomizationMode) {
                homePager.post {
                    updateEditModePageIndicators()
                }
            }
        }
    }
    
    fun navigateToHomePage() {
        // Navigate to first home screen page (page 1, not Cards which is page 0)
        if (isAdded && view != null) {
            val adapter = homePager.adapter
            if (adapter != null && adapter.itemCount > 1) {
                homePager.setCurrentItem(1, true) // Smooth scroll to page 1
            }
        }
    }
    
    private fun setupDock(view: View) {
        try {
            // Find the dock containers
            // The include tag has id="dockContainer" which refers to the outer LinearLayout from dock_layout.xml
            // The outer layout has id="dockScrollView" in dock_layout.xml, but the include's id overrides it
            val dockOuter = view.findViewById<ViewGroup>(R.id.dockContainer) // This is the include/outer layout
            
            if (dockOuter == null) {
                android.util.Log.e("HomeScreenFragment", "Dock outer container not found")
                return
            }
            
            // Find the inner container - it's the first (and only) child LinearLayout
            // Since both have the same ID due to include, we find it by traversing
            var dockContainer: ViewGroup? = null
            for (i in 0 until dockOuter.childCount) {
                val child = dockOuter.getChildAt(i)
                if (child is android.widget.LinearLayout && child.orientation == android.widget.LinearLayout.HORIZONTAL) {
                    dockContainer = child
                    break
                }
            }
            
            // Alternative: try finding by the original ID from the layout file
            if (dockContainer == null) {
                try {
                    // Try to find by the ID that should be in the included layout
                    dockContainer = dockOuter.findViewById(R.id.dockContainer)
                } catch (e: Exception) {
                    android.util.Log.d("HomeScreenFragment", "Could not find dockContainer by ID, trying by index")
                }
            }
            
            // Last resort: get first child if it's a ViewGroup
            if (dockContainer == null && dockOuter.childCount > 0) {
                val firstChild = dockOuter.getChildAt(0)
                if (firstChild is ViewGroup) {
                    dockContainer = firstChild
                    android.util.Log.d("HomeScreenFragment", "Found dock container as first child")
                }
            }
            
            if (dockContainer == null) {
                android.util.Log.e("HomeScreenFragment", "Dock inner container not found. Outer has ${dockOuter.childCount} children")
                return
            }
            
            android.util.Log.d("HomeScreenFragment", "Dock containers found - outer: ${dockOuter.javaClass.simpleName}, inner: ${dockContainer.javaClass.simpleName}")
            setupDockWithContainers(dockOuter, dockContainer, view)
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up dock: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    private fun setupDockWithContainers(dockOuter: ViewGroup, dockContainer: ViewGroup, view: View) {
        try {
            // Update dock background based on theme
            if (isDarkMode(requireContext())) {
                dockOuter.setBackgroundResource(R.drawable.dock_background_dark)
            } else {
                dockOuter.setBackgroundResource(R.drawable.dock_background)
            }
            
            // Make sure dock is visible and has proper layout params
            dockOuter.visibility = View.VISIBLE
            dockContainer.visibility = View.VISIBLE
            
            // Ensure dock has proper layout params (no bottom margin)
            val layoutParams = dockOuter.layoutParams as? android.widget.FrameLayout.LayoutParams
            if (layoutParams != null) {
                layoutParams.bottomMargin = 0 // No gap at the bottom
                layoutParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                dockOuter.layoutParams = layoutParams
                android.util.Log.d("HomeScreenFragment", "Dock layout params updated with no bottom margin")
            }
            
            android.util.Log.d("HomeScreenFragment", "Dock containers found and made visible - outer visibility: ${dockOuter.visibility}, inner visibility: ${dockContainer.visibility}")
            
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
                    android.util.Log.d("HomeScreenFragment", "Loading apps for dock...")
                    val allApps = AppLoader.loadApps(requireContext())
                    android.util.Log.d("HomeScreenFragment", "Loaded ${allApps.size} apps total")
                    
                    var appsAdded = 0
                    // Find and add dock apps
                    dockAppPackages.forEach { packageName ->
                        var app = allApps.find { it.packageName == packageName }
                        
                        // Try alternatives if not found
                        if (app == null) {
                            val alternatives = dockAppPackageAlternatives[packageName] ?: emptyList()
                            for (altPackage in alternatives) {
                                app = allApps.find { it.packageName == altPackage }
                                if (app != null) {
                                    android.util.Log.d("HomeScreenFragment", "Found alternative for $packageName: $altPackage")
                                    break
                                }
                            }
                        }
                        
                        if (app != null) {
                            // Create dock item view on main thread
                            requireActivity().runOnUiThread {
                                val dockItem = createDockItemView(app)
                                dockContainer.addView(dockItem)
                                appsAdded++
                                android.util.Log.d("HomeScreenFragment", "Added dock app: ${app.label} (${app.packageName}). Total dock items: ${dockContainer.childCount}")
                            }
                        } else {
                            android.util.Log.w("HomeScreenFragment", "Dock app not found: $packageName")
                        }
                    }
                    
                    requireActivity().runOnUiThread {
                        android.util.Log.d("HomeScreenFragment", "Dock setup complete. Total apps added: $appsAdded, Total children in dock: ${dockContainer.childCount}")
                        android.util.Log.d("HomeScreenFragment", "Dock outer visibility: ${dockOuter.visibility}, Dock inner visibility: ${dockContainer.visibility}")
                        android.util.Log.d("HomeScreenFragment", "Dock outer height: ${dockOuter.height}, Dock inner height: ${dockContainer.height}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenFragment", "Error loading apps for dock: ${e.message}", e)
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up dock: ${e.message}", e)
        }
    }
    
    private fun setupDockSearch(view: View) {
        try {
            // Find search bar
            dockContainer = view.findViewById(R.id.dockContainer)
            // pageIndicator is already set in setupHomePager(), but ensure it's set here too
            if (pageIndicator == null) {
                pageIndicator = view.findViewById(R.id.pageIndicator)
            }
            val dockSearchBar = dockContainer?.parent?.let { it as? ViewGroup }?.findViewById<View>(R.id.dockSearchBar)
            
            if (dockSearchBar != null) {
                // Set up click listener to open app drawer search
                dockSearchBar.setOnClickListener {
                    // Open app drawer and focus search
                    (activity as? com.cykrome.launcher.ui.LauncherActivity)?.openAppDrawer()
                    // Focus search input after drawer opens
                    view.postDelayed({
                        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.focusDrawerSearch()
                    }, 350)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up dock search: ${e.message}", e)
        }
    }
    
    private fun createDockItemView(app: AppInfo): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        
        // Create a custom dock item view with background
        val dockItem = android.widget.FrameLayout(context)
        
        // Set layout params for LinearLayout parent (horizontal orientation)
        val itemSize = (64 * density).toInt() // 64dp
        val margin = (8 * density).toInt() // 8dp margin between items
        val params = android.widget.LinearLayout.LayoutParams(
            itemSize,
            itemSize
        )
        params.setMargins(margin, 0, margin, 0)
        dockItem.layoutParams = params
        
        // Add background with rounded corners
        val backgroundDrawable = if (isDarkMode(context)) {
            context.getDrawable(R.drawable.app_icon_background_dark)
        } else {
            context.getDrawable(R.drawable.app_icon_background)
        }
        dockItem.background = backgroundDrawable
        
        // Add padding
        val padding = (8 * density).toInt()
        dockItem.setPadding(padding, padding, padding, padding)
        
        // Add elevation
        dockItem.elevation = 4f
        
        // Add click effect - use TypedArray to resolve attribute
        val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val selectableItemBackground = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        if (selectableItemBackground != 0) {
            dockItem.foreground = context.getDrawable(selectableItemBackground)
        }
        dockItem.isClickable = true
        dockItem.isFocusable = true
        
        // Make sure dock item is visible
        dockItem.visibility = View.VISIBLE
        
        // Create icon view
        val iconView = android.widget.ImageView(context)
        val iconSize = (48 * density).toInt() // 48dp icon
        val iconParams = android.widget.FrameLayout.LayoutParams(
            iconSize,
            iconSize
        )
        iconParams.gravity = android.view.Gravity.CENTER
        iconView.layoutParams = iconParams
        iconView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        iconView.adjustViewBounds = true
        
        // Make sure icon is visible
        iconView.visibility = View.VISIBLE
        
        // Set app icon
        val packageManager = context.packageManager
        try {
            val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
            iconView.setImageDrawable(packageManager.getApplicationIcon(appInfo))
            android.util.Log.d("HomeScreenFragment", "Set icon for ${app.label}: ${app.packageName}")
        } catch (e: Exception) {
            android.util.Log.w("HomeScreenFragment", "Failed to load icon for ${app.packageName}: ${e.message}")
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        dockItem.addView(iconView)
        
        // Set click listener to launch app
        dockItem.setOnClickListener {
            launchApp(app)
        }
        
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
    
    private fun setupCustomizationOverlay(view: View) {
        try {
            // Inflate the customization overlay
            // Add it to the fragment's root view (which is a ConstraintLayout)
            val rootView = view as? ViewGroup
            val overlayView = LayoutInflater.from(requireContext()).inflate(
                R.layout.customization_overlay,
                rootView,
                false
            )
            
            customizationOverlay = overlayView
            editModePageIndicator = overlayView.findViewById(R.id.editModePageIndicator)
            
            // Initially hide the overlay
            overlayView.visibility = View.GONE
            
            // Add overlay to the fragment's root view
            rootView?.addView(overlayView)
            
            // Set up click listener to dismiss overlay when clicking outside (but not on interactive elements)
            // Use a simpler approach: let child views handle their clicks first, overlay only handles background clicks
            overlayView.setOnClickListener {
                // This will only fire if no child view consumed the click
                hideCustomizationOverlay()
            }
            
            // Prevent clicks on menu bar from dismissing overlay
            overlayView.findViewById<View>(R.id.customizationMenuBar)?.setOnClickListener {
                // Do nothing - prevent dismissal
            }
            
            // Prevent clicks on page indicators from dismissing overlay
            overlayView.findViewById<View>(R.id.pageIndicatorContainer)?.setOnClickListener {
                // Do nothing - prevent dismissal
            }
            
            // Set up menu bar button clicks
            setupMenuBarButtons(overlayView)
            
            // Set up add page button - use FrameLayout like other menu buttons
            val addPageButton = overlayView.findViewById<View>(R.id.addPageButton)
            addPageButton?.setOnClickListener {
                android.util.Log.d("HomeScreenFragment", "Add page button clicked! Button: $addPageButton")
                addNewPage()
            }
            android.util.Log.d("HomeScreenFragment", "Add page button setup: ${addPageButton != null}")
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up customization overlay: ${e.message}", e)
        }
    }
    
    private fun setupCombinedTouchListener() {
        var swipeDownStartY = 0f
        var swipeDownStartX = 0f
        var isTrackingSwipeDown = false
        var longPressStartX = 0f
        var longPressStartY = 0f
        var isLongPressDetected = false
        var longPressRunnable: Runnable? = null
        
        homePager.setOnTouchListener { v, event ->
            android.util.Log.d("HomeScreenFragment", "ViewPager2 touch: action=${event.action}, x=${event.x}, y=${event.y}, isCustomizationMode=$isCustomizationMode")
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownStartY = event.y
                    swipeDownStartX = event.x
                    isTrackingSwipeDown = true
                    
                    // Set up long press detection if not in customization mode
                    if (!isCustomizationMode) {
                        longPressStartX = event.x
                        longPressStartY = event.y
                        isLongPressDetected = false
                        
                        // Post a delayed runnable to show overlay after long press timeout
                        longPressRunnable = Runnable {
                            if (!isLongPressDetected && !isCustomizationMode) {
                                android.util.Log.d("HomeScreenFragment", "Long press detected! Showing customization overlay")
                                showCustomizationOverlay()
                                isLongPressDetected = true
                            }
                        }
                        val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        android.util.Log.d("HomeScreenFragment", "Scheduling long press detection with timeout: ${longPressTimeout}ms")
                        v.postDelayed(longPressRunnable!!, longPressTimeout)
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrackingSwipeDown) {
                        val deltaY = event.y - swipeDownStartY
                        val deltaX = Math.abs(event.x - swipeDownStartX)
                        val absDeltaY = Math.abs(deltaY)
                        val minSwipeDistance = android.view.ViewConfiguration.get(v.context).scaledTouchSlop * 2
                        
                        // Check if it's a vertical swipe down (not horizontal)
                        if (deltaY > 0 && absDeltaY > deltaX && absDeltaY > minSwipeDistance) {
                            android.util.Log.d("HomeScreenFragment", "Swipe down detected in ViewPager2! Calling handleGesture")
                            isTrackingSwipeDown = false
                            // Cancel long press if swipe down detected
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            longPressRunnable = null
                            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.let { launcher ->
                                val prefs = com.cykrome.launcher.data.LauncherPreferences(v.context)
                                launcher.handleGesture(prefs.swipeDownAction)
                            }
                            return@setOnTouchListener true
                        }
                        
                        // If horizontal movement is too much, cancel tracking
                        if (deltaX > absDeltaY) {
                            isTrackingSwipeDown = false
                        }
                    }
                    
                    // Handle long press cancellation on movement
                    if (!isCustomizationMode && !isLongPressDetected && longPressRunnable != null) {
                        val deltaX = Math.abs(event.x - longPressStartX)
                        val deltaY = Math.abs(event.y - longPressStartY)
                        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        
                        // If moved too much, cancel long press (increased threshold)
                        val touchSlop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop
                        val movementThreshold = touchSlop * 4 // Increased from 2 to 4
                        if (distance > movementThreshold) {
                            android.util.Log.d("HomeScreenFragment", "Long press cancelled due to movement: distance=$distance, threshold=$movementThreshold")
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            longPressRunnable = null
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTrackingSwipeDown = false
                    longPressRunnable?.let { homePager.removeCallbacks(it) }
                    longPressRunnable = null
                    isLongPressDetected = false
                    false
                }
                else -> false
            }
            
            // Forward touch events to parent activity for other gestures (swipe up, drawer drag)
            val handled = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.onTouchEvent(event) ?: false
            // If parent is handling it (e.g., dragging drawer), consume it here too
            handled
        }
    }
    
    
    fun showCustomizationOverlay() {
        try {
            customizationOverlay?.let { overlay ->
                isCustomizationMode = true
                overlay.visibility = View.VISIBLE
                
                // Update page indicators in edit mode
                updateEditModePageIndicators()
                
                // Hide regular page indicator
                pageIndicator?.visibility = View.GONE
                
                // Hide dock with slide down animation
                dockContainer?.let { dock ->
                    dock.animate()
                        .translationY(dock.height.toFloat())
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            dock.visibility = View.GONE
                        }
                        .start()
                }
                
                // Set up back button handler
                setupBackButtonHandler()
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error showing customization overlay: ${e.message}", e)
        }
    }
    
    fun hideCustomizationOverlay() {
        try {
            customizationOverlay?.let { overlay ->
                isCustomizationMode = false
                overlay.visibility = View.GONE
                
                // Show regular page indicator
                val currentPage = homePager.currentItem
                pageIndicator?.visibility = if (currentPage == 0) View.GONE else View.VISIBLE
                
                // Show dock with slide up animation
                dockContainer?.let { dock ->
                    dock.visibility = View.VISIBLE
                    dock.translationY = dock.height.toFloat()
                    dock.alpha = 0f
                    dock.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                
                // Remove back button handler
                removeBackButtonHandler()
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error hiding customization overlay: ${e.message}", e)
        }
    }
    
    private var backButtonCallback: OnBackPressedCallback? = null
    
    private fun setupBackButtonHandler() {
        removeBackButtonHandler() // Remove any existing callback first
        
        backButtonCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                hideCustomizationOverlay()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backButtonCallback!!)
    }
    
    private fun removeBackButtonHandler() {
        backButtonCallback?.remove()
        backButtonCallback = null
    }
    
    private fun updateEditModePageIndicators() {
        try {
            editModePageIndicator?.let { indicator ->
                val adapter = homePager.adapter as? DesktopPageAdapter
                val pageCount = adapter?.itemCount ?: 1
                val homeScreenPageCount = (pageCount - 1).coerceAtLeast(1) // Exclude Cards page
                
                // Remove existing listener to avoid duplicates
                indicator.clearOnTabSelectedListeners()
                indicator.removeAllTabs()
                
                for (i in 0 until homeScreenPageCount) {
                    indicator.addTab(indicator.newTab())
                }
                
                // Post to ensure tabs are laid out, then fix their dimensions to maintain circular shape
                indicator.post {
                    for (i in 0 until indicator.tabCount) {
                        val tab = indicator.getTabAt(i)
                        tab?.let {
                            try {
                                val tabView = it.view
                                val density = resources.displayMetrics.density
                                val size = (10 * density).toInt()
                                // Remove all padding to prevent elongation
                                tabView.setPadding(0, 0, 0, 0)
                                // Set fixed dimensions to maintain circular shape
                                tabView.minimumHeight = size
                                tabView.minimumWidth = size
                                // Force layout params to maintain square shape
                                val layoutParams = tabView.layoutParams
                                if (layoutParams != null) {
                                    layoutParams.height = size
                                    layoutParams.width = size
                                    tabView.layoutParams = layoutParams
                                } else {
                                    // Create new layout params if null
                                    val newParams = android.view.ViewGroup.LayoutParams(size, size)
                                    tabView.layoutParams = newParams
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HomeScreenFragment", "Error setting tab dimensions: ${e.message}")
                            }
                        }
                    }
                }
                
                // Set current page selection
                val currentPage = homePager.currentItem
                if (currentPage > 0 && currentPage - 1 < homeScreenPageCount) {
                    indicator.getTabAt(currentPage - 1)?.select()
                }
                
                // Add listener for page changes
                indicator.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        tab?.let {
                            val pageIndex = it.position
                            // Map tab index to ViewPager position (tab 0 = page 1, tab 1 = page 2, etc.)
                            homePager.setCurrentItem(pageIndex + 1, true)
                        }
                    }
                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error updating edit mode page indicators: ${e.message}", e)
        }
    }
    
    private fun setupMenuBarButtons(overlayView: View) {
        try {
            // Widgets button
            overlayView.findViewById<View>(R.id.widgetsButton)?.setOnClickListener {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Widgets feature coming soon",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            
            // Effects button
            overlayView.findViewById<View>(R.id.effectsButton)?.setOnClickListener {
                // Open scroll effects settings
                val intent = android.content.Intent(requireContext(), com.cykrome.launcher.ui.settings.SettingsActivity::class.java)
                startActivity(intent)
                hideCustomizationOverlay()
            }
            
            // Wallpapers button
            overlayView.findViewById<View>(R.id.wallpapersButton)?.setOnClickListener {
                // Open wallpaper picker
                try {
                    val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to system wallpaper picker
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                        startActivity(android.content.Intent.createChooser(intent, "Select Wallpaper"))
                    } catch (e2: Exception) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Wallpaper picker not available",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                hideCustomizationOverlay()
            }
            
            // CyKrome Settings button
            overlayView.findViewById<View>(R.id.homeSettingsButton)?.setOnClickListener {
                // Open CyKrome Settings
                val intent = android.content.Intent(requireContext(), com.cykrome.launcher.ui.settings.SettingsActivity::class.java)
                startActivity(intent)
                hideCustomizationOverlay()
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error setting up menu bar buttons: ${e.message}", e)
        }
    }
    
    fun addNewPage() {
        try {
            android.util.Log.d("HomeScreenFragment", "addNewPage() called")
            
            // Get current adapter to check current page count
            val currentAdapter = homePager.adapter as? DesktopPageAdapter
            val currentAdapterItemCount = currentAdapter?.itemCount ?: 1
            val currentHomeScreenPages = (currentAdapterItemCount - 1).coerceAtLeast(1) // Exclude Cards page
            
            android.util.Log.d("HomeScreenFragment", "Current adapter item count: $currentAdapterItemCount, home screen pages: $currentHomeScreenPages")
            
            // Calculate new page count
            val newPageCount = currentHomeScreenPages + 1
            
            // Save to preferences (use commit for synchronous write)
            val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            sharedPrefs.edit().putInt("home_screen_page_count", newPageCount).commit()
            android.util.Log.d("HomeScreenFragment", "Saved new page count: $newPageCount")
            
            // Reload home screen items
            val homeScreenItems = com.cykrome.launcher.util.HomeScreenManager.loadHomeScreenItems(requireContext())
            val appItems = homeScreenItems.filterIsInstance<com.cykrome.launcher.model.HomeScreenItem.AppItem>()
            
            // Convert to AppInfo list
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
            
            // Create new adapter with new page count
            val newAdapter = DesktopPageAdapter(this, homeScreenApps, appItems, preferences, newPageCount)
            android.util.Log.d("HomeScreenFragment", "Created new adapter with page count: $newPageCount, item count: ${newAdapter.itemCount}")
            
            // Set the new adapter
            homePager.adapter = newAdapter
            
            // Wait for adapter to be ready, then navigate and update indicators
            homePager.postDelayed({
                try {
                    val finalAdapter = homePager.adapter as? DesktopPageAdapter
                    val finalItemCount = finalAdapter?.itemCount ?: newAdapter.itemCount
                    android.util.Log.d("HomeScreenFragment", "Final adapter item count: $finalItemCount, expected: ${newPageCount + 1}")
                    
                    if (finalItemCount > 1) {
                        // Update edit mode indicators
                        updateEditModePageIndicators()
                        
                        // Navigate to the new page (last page)
                        val newPagePosition = finalItemCount - 1
                        android.util.Log.d("HomeScreenFragment", "Navigating to new page at position: $newPagePosition")
                        homePager.setCurrentItem(newPagePosition, true)
                        
                        android.widget.Toast.makeText(
                            requireContext(),
                            "New page added",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.util.Log.w("HomeScreenFragment", "Adapter item count is still 1, expected: ${newPageCount + 1}")
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to add page. Current: $finalItemCount, Expected: ${newPageCount + 1}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenFragment", "Error in addNewPage post: ${e.message}", e)
                    e.printStackTrace()
                }
            }, 100) // Small delay to ensure adapter is set
        } catch (e: Exception) {
            android.util.Log.e("HomeScreenFragment", "Error adding new page: ${e.message}", e)
            e.printStackTrace()
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
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    
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
            recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.desktopGrid)
            val columns = preferences.homeGridColumns
            val layoutManager = GridLayoutManager(requireContext(), columns)
            recyclerView?.layoutManager = layoutManager
            
            // Ensure RecyclerView can scroll vertically
            recyclerView?.isNestedScrollingEnabled = true
            
            // Add top padding to RecyclerView to account for search bar and status bar
            val statusBarHeight = getStatusBarHeightForPage()
            val searchBarHeight = (60 * resources.displayMetrics.density).toInt() // Approximate search bar height
            val extraPadding = (16 * resources.displayMetrics.density).toInt() // 16dp
            val totalTopPadding = statusBarHeight + searchBarHeight + extraPadding
            
            recyclerView?.setPadding(
                recyclerView?.paddingLeft ?: 0,
                totalTopPadding,
                recyclerView?.paddingRight ?: 0,
                recyclerView?.paddingBottom ?: 0
            )
            
            // Set up drag and drop listener for apps from drawer
            recyclerView?.let { setupDragAndDropFromDrawer(it) }
            
            // Set up gesture interceptor for swipe up to open app drawer, swipe down for notifications, and long press for customization
            // Use a custom touch listener that checks for swipe gestures and long press
            var swipeDownStartX = 0f
            var isTrackingSwipeDown = false
            var longPressStartX = 0f
            var longPressStartY = 0f
            var isLongPressDetected = false
            var longPressRunnable: Runnable? = null
            var isTouchOnIcon = false
            
            recyclerView?.setOnTouchListener { v, event ->
                android.util.Log.d("DesktopPageFragment", "RecyclerView touch: action=${event.action}, x=${event.x}, y=${event.y}")
                
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        swipeStartY = event.y
                        swipeDownStartX = event.x
                        isTrackingSwipeDown = true
                        
                        // Check if touch is on an app icon
                        isTouchOnIcon = isTouchOnAppIcon(event.x, event.y)
                        
                        // Set up long press detection if not touching an icon
                        val homeScreenFragment = parentFragment as? HomeScreenFragment
                        if (!isTouchOnIcon && homeScreenFragment != null && !homeScreenFragment.isCustomizationMode) {
                            longPressStartX = event.x
                            longPressStartY = event.y
                            isLongPressDetected = false
                            
                            longPressRunnable = Runnable {
                                if (!isLongPressDetected && !isTouchOnIcon) {
                                    android.util.Log.d("DesktopPageFragment", "Long press detected on empty area! Showing customization overlay")
                                    homeScreenFragment.showCustomizationOverlay()
                                    isLongPressDetected = true
                                }
                            }
                            val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                            v.postDelayed(longPressRunnable!!, longPressTimeout)
                        }
                        
                        android.util.Log.d("DesktopPageFragment", "Started tracking swipe in RecyclerView, isTouchOnIcon=$isTouchOnIcon")
                        false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - swipeStartY
                        val deltaX = Math.abs(event.x - swipeDownStartX)
                        val absDeltaY = Math.abs(deltaY)
                        val minSwipeDistance = android.view.ViewConfiguration.get(v.context).scaledTouchSlop * 2
                        
                        android.util.Log.d("DesktopPageFragment", "MOVE: deltaY=$deltaY, deltaX=$deltaX, absDeltaY=$absDeltaY")
                        
                        // Handle long press cancellation on movement
                        if (longPressRunnable != null && !isLongPressDetected) {
                            val moveDeltaX = Math.abs(event.x - longPressStartX)
                            val moveDeltaY = Math.abs(event.y - longPressStartY)
                            val moveDistance = Math.sqrt((moveDeltaX * moveDeltaX + moveDeltaY * moveDeltaY).toDouble()).toFloat()
                            
                            val touchSlop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop
                            val movementThreshold = touchSlop * 4 // Increased threshold
                            if (moveDistance > movementThreshold) {
                                android.util.Log.d("DesktopPageFragment", "Long press cancelled due to movement: distance=$moveDistance, threshold=$movementThreshold")
                                longPressRunnable?.let { v.removeCallbacks(it) }
                                longPressRunnable = null
                            }
                        }
                        
                        // Check for swipe down (for notifications)
                        if (isTrackingSwipeDown && deltaY > 0 && absDeltaY > deltaX && absDeltaY > minSwipeDistance) {
                            // Cancel long press if swipe down detected
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            longPressRunnable = null
                            
                            // Check if RecyclerView is at the top (can't scroll up)
                            val rv = recyclerView
                            if (rv != null && !rv.canScrollVertically(-1)) {
                                android.util.Log.d("DesktopPageFragment", "Swipe down detected in RecyclerView! Calling handleGesture")
                                isTrackingSwipeDown = false
                                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.let { launcher ->
                                    val prefs = com.cykrome.launcher.data.LauncherPreferences(v.context)
                                    android.util.Log.d("DesktopPageFragment", "Swipe down action: ${prefs.swipeDownAction}")
                                    launcher.handleGesture(prefs.swipeDownAction)
                                }
                                return@setOnTouchListener true
                            }
                        }
                        
                        // If swiping up and RecyclerView is at the top, let parent handle it
                        val rv = recyclerView
                        if (deltaY < -50 && rv != null && !rv.canScrollVertically(-1)) {
                            // Cancel long press if swiping up
                            longPressRunnable?.let { v.removeCallbacks(it) }
                            longPressRunnable = null
                            // RecyclerView is at top and user is swiping up - let parent handle
                            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.onTouchEvent(event)
                            isTrackingSwipeDown = false
                            true // Consume the event
                        } else {
                            // If horizontal movement is too much, cancel swipe down tracking
                            if (deltaX > absDeltaY) {
                                isTrackingSwipeDown = false
                            }
                            false // Let RecyclerView handle scrolling
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaY = event.y - swipeStartY
                        isTrackingSwipeDown = false
                        
                        // Cancel long press
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        longPressRunnable = null
                        isLongPressDetected = false
                        
                        // If it was a swipe up and RecyclerView was at top, trigger app drawer
                        val rv = recyclerView
                        if (deltaY < -100 && rv != null && !rv.canScrollVertically(-1)) {
                            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.openAppDrawer()
                            true
                        } else {
                            false
                        }
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        isTrackingSwipeDown = false
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        longPressRunnable = null
                        isLongPressDetected = false
                        false
                    }
                    else -> false
                }
            }
            
            val adapter = AppIconAdapter(apps.toMutableList(), preferences, requireContext())
            recyclerView?.adapter = adapter
            
            // Set up drag and drop for rearranging apps
            recyclerView?.let { setupDragAndDrop(it, adapter) }
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
    
    fun isTouchOnAppIcon(x: Float, y: Float): Boolean {
        recyclerView?.let { rv ->
            // Coordinates are relative to RecyclerView
            // Find the child view at this position
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val childLocation = IntArray(2)
                child.getLocationOnScreen(childLocation)
                val rvLocation = IntArray(2)
                rv.getLocationOnScreen(rvLocation)
                
                // Convert touch coordinates to screen coordinates
                val screenX = rvLocation[0] + x
                val screenY = rvLocation[1] + y
                
                if (screenX >= childLocation[0] && screenX <= childLocation[0] + child.width &&
                    screenY >= childLocation[1] && screenY <= childLocation[1] + child.height) {
                    return true
                }
            }
        }
        return false
    }
    
    val isCustomizationMode: Boolean
        get() = (parentFragment as? HomeScreenFragment)?.isCustomizationMode ?: false
    
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
        // Return total pages + 1 for Cards page (Cards is at position 0)
        return totalPages.coerceAtLeast(1) + 1
    }
    
    override fun createFragment(position: Int): Fragment {
        // Position 0 is the Cards page
        if (position == 0) {
            return CardsFragment.newInstance()
        }
        
        // Position 1+ are home screen pages (adjust page number by -1)
        val homeScreenPage = position - 1
        
        // Get apps for this specific page
        val pageApps = appItems
            .filter { it.page == homeScreenPage }
            .mapNotNull { item ->
                // Find the corresponding AppInfo
                allApps.find { it.packageName == item.packageName && it.activityName == item.activityName }
            }
        
        return DesktopPageFragment.newInstance(pageApps, appItems.filter { it.page == homeScreenPage }, preferences)
    }
}

