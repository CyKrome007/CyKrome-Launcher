package com.cykrome.launcher.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.ui.adapters.AppIconAdapter
import com.cykrome.launcher.util.AppLoader
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AppDrawerFragment : Fragment() {
    
    private lateinit var drawerPager: ViewPager2
    private lateinit var drawerTabs: com.google.android.material.tabs.TabLayout
    private lateinit var preferences: LauncherPreferences
    private var apps: List<AppInfo> = emptyList()
    private var searchInput: TextInputEditText? = null
    private var searchResultsContainer: ViewGroup? = null
    private var searchResultsRecyclerView: RecyclerView? = null
    private var searchClearButton: View? = null
    private var allApps: List<AppInfo> = emptyList()
    private var searchAdapter: AppIconAdapter? = null
    private var currentDrawerStyle: String? = null
    private var tabLayoutMediator: TabLayoutMediator? = null
    private var scrollHandle: View? = null
    
    companion object {
        fun newInstance(): AppDrawerFragment {
            return AppDrawerFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_drawer, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferences = LauncherPreferences(requireContext())
        drawerPager = view.findViewById(R.id.drawerPager)
        drawerTabs = view.findViewById(R.id.drawerTabs)
        searchInput = view.findViewById(R.id.drawerSearchInput)
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer)
        searchResultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        searchClearButton = view.findViewById(R.id.drawerSearchClear)
        scrollHandle = view.findViewById(R.id.scrollHandle)
        
        // Add top padding to AppBarLayout to account for status bar
        adjustDrawerTopPadding(view)
        
        // Set up search functionality
        setupSearch()
        
        loadApps()
        
        // Set up swipe down to close drawer
        setupSwipeToClose()
        
        // Close drawer on outside click (only if clicking on empty space, not on apps)
        // Removed to prevent accidental closes
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if drawer style has changed and refresh if needed
        if (::preferences.isInitialized && ::drawerPager.isInitialized) {
            val newDrawerStyle = preferences.drawerStyle
            if (currentDrawerStyle != null && currentDrawerStyle != newDrawerStyle) {
                // Drawer style changed, refresh the drawer
                android.util.Log.d("AppDrawerFragment", "Drawer style changed from $currentDrawerStyle to $newDrawerStyle, refreshing...")
                if (apps.isNotEmpty()) {
                    view?.post {
                        setupDrawer()
                    }
                }
            }
            currentDrawerStyle = newDrawerStyle
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up TabLayoutMediator
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
    }
    
    private fun setupSearch() {
        // Set up search results RecyclerView
        searchResultsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        
        // Handle back button press - clear search if search input has focus
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (searchInput?.hasFocus() == true || !searchInput?.text.isNullOrEmpty()) {
                    // Search input has focus or has text - clear search instead of closing drawer
                    searchInput?.clearFocus()
                    searchInput?.setText("")
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(searchInput?.windowToken, 0)
                } else {
                    // No search active, let normal back behavior close drawer
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        
        searchInput?.let { input ->
            // Set up text change listener for real-time search and back callback state
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString() ?: ""
                    filterApps(query)
                    // Enable/disable back callback based on search state
                    backCallback.isEnabled = !query.isEmpty() || input.hasFocus()
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
            
            // Show keyboard when focused and update back callback state
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                // Update back callback state
                backCallback.isEnabled = hasFocus || !input.text.isNullOrEmpty()
            }
        }
        
        // Set up clear button
        searchClearButton?.setOnClickListener {
            searchInput?.setText("")
            searchInput?.requestFocus()
        }
    }
    
    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            // Hide search results, show normal drawer
            searchResultsContainer?.visibility = View.GONE
            drawerPager.visibility = View.VISIBLE
            drawerTabs.visibility = View.VISIBLE
            searchClearButton?.visibility = View.GONE
            // Clear focus from search input
            searchInput?.clearFocus()
        } else {
            // Show search results, hide normal drawer
            searchResultsContainer?.visibility = View.VISIBLE
            drawerPager.visibility = View.GONE
            drawerTabs.visibility = View.GONE
            searchClearButton?.visibility = View.VISIBLE
            
            // Filter apps
            val filtered = allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
            
            // Create search results with apps and suggestions
            val searchResults = mutableListOf<SearchResultItem>()
            
            // Add app results
            filtered.take(5).forEach { app ->
                searchResults.add(SearchResultItem(SearchResultItem.Type.APP, app, null))
            }
            
            // Add text suggestions (simple implementation - can be enhanced later)
            val suggestions = generateTextSuggestions(query)
            suggestions.forEach { suggestion ->
                searchResults.add(SearchResultItem(SearchResultItem.Type.TEXT_SUGGESTION, null, suggestion))
            }
            
            // Update adapter
            val adapter = SearchResultsAdapter(searchResults, preferences, requireContext())
            adapter.onAppClick = { app ->
                val intent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    requireContext().startActivity(intent)
                    (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                }
            }
            adapter.onSuggestionClick = { suggestion ->
                // Handle suggestion click - for now, just set it as search text
                searchInput?.setText(suggestion)
                searchInput?.setSelection(suggestion.length)
            }
            searchResultsRecyclerView?.adapter = adapter
        }
    }
    
    private fun generateTextSuggestions(query: String): List<String> {
        // Generate simple suggestions based on query
        // In a real implementation, this could use a search API or local database
        val suggestions = mutableListOf<String>()
        
        // Add query variations
        if (query.length > 2) {
            suggestions.add("$query chapter 1")
            suggestions.add("$query sambad")
            suggestions.add("lovable")
            suggestions.add("${query} time now")
        }
        
        return suggestions.take(5)
    }
    
    private data class SearchResultItem(
        val type: Type,
        val app: AppInfo?,
        val suggestion: String?
    ) {
        enum class Type {
            APP,
            TEXT_SUGGESTION
        }
    }
    
    private class SearchResultsAdapter(
        private val items: List<SearchResultItem>,
        private val preferences: LauncherPreferences,
        private val context: android.content.Context
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        var onAppClick: ((AppInfo) -> Unit)? = null
        var onSuggestionClick: ((String) -> Unit)? = null
        
        override fun getItemViewType(position: Int): Int {
            return when (items[position].type) {
                SearchResultItem.Type.APP -> 0
                SearchResultItem.Type.TEXT_SUGGESTION -> 1
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> {
                    // App item
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_search_app, parent, false)
                    AppViewHolder(view)
                }
                1 -> {
                    // Text suggestion item
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_search_suggestion, parent, false)
                    SuggestionViewHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type")
            }
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is SearchResultItem -> {
                    when (item.type) {
                        SearchResultItem.Type.APP -> {
                            (holder as AppViewHolder).bind(item.app!!, onAppClick)
                        }
                        SearchResultItem.Type.TEXT_SUGGESTION -> {
                            (holder as SuggestionViewHolder).bind(item.suggestion!!, onSuggestionClick)
                        }
                    }
                }
            }
        }
        
        override fun getItemCount(): Int = items.size
        
        class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView = itemView.findViewById<android.widget.ImageView>(R.id.appIcon)
            private val labelView = itemView.findViewById<android.widget.TextView>(R.id.appLabel)
            
            fun bind(app: AppInfo, onAppClick: ((AppInfo) -> Unit)?) {
                iconView?.setImageDrawable(app.icon)
                labelView?.text = app.label
                
                itemView.setOnClickListener {
                    onAppClick?.invoke(app)
                }
            }
        }
        
        class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(suggestion: String, onSuggestionClick: ((String) -> Unit)?) {
                val textView = itemView.findViewById<android.widget.TextView>(R.id.suggestionText)
                textView?.text = suggestion
                
                itemView.setOnClickListener {
                    onSuggestionClick?.invoke(suggestion)
                }
            }
        }
    }
    
    private fun adjustDrawerTopPadding(view: View) {
        try {
            val appBarLayout = view.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
            val drawerTabs = view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.drawerTabs)
            if (appBarLayout != null) {
                // Remove all padding from AppBarLayout to eliminate any space
                appBarLayout.setPadding(0, 0, 0, 0)
                
                // Remove all padding from TabLayout to eliminate pink/grey space
                if (drawerTabs != null) {
                    drawerTabs.setPadding(0, 0, 0, 0)
                    // Also set margin to 0 to ensure no extra space
                    val layoutParams = drawerTabs.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                    layoutParams?.topMargin = 0
                    drawerTabs.layoutParams = layoutParams
                }
                
                android.util.Log.d("AppDrawerFragment", "Removed all padding and margins from drawer top")
            }
        } catch (e: Exception) {
            android.util.Log.e("AppDrawerFragment", "Error adjusting drawer top padding: ${e.message}", e)
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
    
    private fun setupSwipeToClose() {
        val appBarLayout = view?.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        var startY = 0f
        var isDragging = false
        var velocityTracker: android.view.VelocityTracker? = null
        
        // Helper function to handle swipe down gesture
        val handleSwipeDown = { v: View, event: android.view.MotionEvent, isFromAppBar: Boolean ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    isDragging = false
                    velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.y - startY
                    // Only trigger if swiping down
                    if (deltaY > 20) {
                        if (!isDragging) {
                            isDragging = true
                            // Get the drawer container from activity
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        
                        // Apply translation to drawer container (not fragment view) - this prevents stretching
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                            drawerContainer.translationY = translation
                            drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                        }
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val deltaY = event.y - startY
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val currentTranslation = drawerContainer.translationY
                            
                            // Calculate velocity for more responsive closing
                            velocityTracker?.computeCurrentVelocity(1000)
                            val velocityY = velocityTracker?.yVelocity ?: 0f
                            velocityTracker?.recycle()
                            velocityTracker = null
                            
                            // Reduced threshold: 10% of screen or 80dp, whichever is smaller
                            // Also close if velocity is high (fast swipe)
                            val minThreshold = 80 * resources.displayMetrics.density
                            val maxThreshold = screenHeight * 0.15f // 15% of screen
                            val threshold = minThreshold.coerceAtMost(maxThreshold)
                            val velocityThreshold = 500f // Close if swiping faster than 500px/s
                            
                            if (currentTranslation > threshold || deltaY > threshold || velocityY > velocityThreshold) {
                                // Close drawer with reverse animation
                                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                            } else {
                                // Snap back to original position with smooth animation
                                drawerContainer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        
                        // Re-enable touch events
                        drawerContainer?.parent?.requestDisallowInterceptTouchEvent(false)
                        isDragging = false
                        true
                    } else {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        false
                    }
                }
                else -> false
            }
        }
        
        // Set up swipe down gesture on AppBarLayout (TabLayout area) - always allow closing from here
        appBarLayout?.setOnTouchListener { v, event ->
            handleSwipeDown(v, event, true)
        }
        
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            apps = AppLoader.loadApps(requireContext(), preferences.hiddenApps)
            allApps = apps // Store all apps for search
            currentDrawerStyle = preferences.drawerStyle
            setupDrawer()
        }
    }
    
    fun focusSearchInput() {
        searchInput?.requestFocus()
        searchInput?.post {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun setupDrawer() {
        if (!::drawerPager.isInitialized || !::drawerTabs.isInitialized) {
            android.util.Log.w("AppDrawerFragment", "Views not initialized, skipping setupDrawer")
            return
        }
        
        if (view == null || !isAdded) {
            android.util.Log.w("AppDrawerFragment", "Fragment view not ready, skipping setupDrawer")
            return
        }
        
        // Detach existing TabLayoutMediator if any
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        
        try {
            // Clear existing adapter
            drawerPager.adapter = null
            
            // Reset tabs visibility
            drawerTabs.visibility = View.VISIBLE
            
            when (preferences.drawerStyle) {
                LauncherPreferences.DRAWER_STYLE_VERTICAL -> {
                    setupVerticalDrawer()
                }
                LauncherPreferences.DRAWER_STYLE_ALPHA_VERTICAL -> {
                    setupAlphaVerticalDrawer()
                }
                LauncherPreferences.DRAWER_STYLE_HORIZONTAL -> {
                    setupHorizontalDrawer()
                }
                LauncherPreferences.DRAWER_STYLE_ALPHA_HORIZONTAL -> {
                    setupAlphaHorizontalDrawer()
                }
                LauncherPreferences.DRAWER_STYLE_LIST -> {
                    setupListDrawer()
                }
                LauncherPreferences.DRAWER_STYLE_ALPHA_LIST -> {
                    setupAlphaListDrawer()
                }
                else -> {
                    // Fallback to vertical if unknown style
                    android.util.Log.w("AppDrawerFragment", "Unknown drawer style: ${preferences.drawerStyle}, using vertical")
                    setupVerticalDrawer()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppDrawerFragment", "Error setting up drawer: ${e.message}", e)
            e.printStackTrace()
            // Fallback to vertical on error
            try {
                setupVerticalDrawer()
            } catch (e2: Exception) {
                android.util.Log.e("AppDrawerFragment", "Error in fallback setup: ${e2.message}", e2)
                e2.printStackTrace()
            }
        }
    }
    
    // Vertical: Continuous vertical scroll, all apps in alphabetical order
    private fun setupVerticalDrawer() {
        drawerTabs.visibility = View.GONE
        showScrollHandle(true)
        
        // Create a single page with all apps sorted alphabetically
        val sortedApps = apps.sortedBy { it.label.lowercase() }
        val adapter = SinglePageDrawerAdapter(this, sortedApps, preferences, false)
        drawerPager.adapter = adapter
        
        // Set up scroll handle position tracking
        setupScrollHandleTracking()
    }
    
    // Alpha Vertical: Vertical scroll with section headers, each letter starts on new line
    private fun setupAlphaVerticalDrawer() {
        drawerTabs.visibility = View.GONE
        showScrollHandle(true)
        
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No apps available for Alpha Vertical drawer")
            return
        }
        
        // Group apps by first letter and sort
        val groupedApps = apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
        val categories = groupedApps.keys.sorted()
        
        // Create a list with section headers and apps
        val itemsWithHeaders = mutableListOf<DrawerItem>()
        categories.forEach { letter ->
            itemsWithHeaders.add(DrawerItem.Header(letter))
            val letterApps = (groupedApps[letter] ?: emptyList()).sortedBy { it.label.lowercase() }
            letterApps.forEach { app ->
                itemsWithHeaders.add(DrawerItem.App(app))
            }
        }
        
        if (itemsWithHeaders.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No items to display in Alpha Vertical drawer")
            return
        }
        
        val adapter = AlphaVerticalDrawerAdapter(this, itemsWithHeaders, preferences)
        drawerPager.adapter = adapter
        
        // Set up scroll handle position tracking
        setupScrollHandleTracking()
    }
    
    // Alpha Horizontal: Pages segregated by letter (current implementation)
    private fun setupAlphaHorizontalDrawer() {
        showScrollHandle(false)
        
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No apps available for Alpha Horizontal drawer")
            drawerTabs.visibility = View.GONE
            return
        }
        
        val adapter = AppDrawerPageAdapter(this, apps, preferences)
        drawerPager.adapter = adapter
        
        // Group apps by first letter
        val groupedApps = apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
        val categories = groupedApps.keys.sorted()
        
        if (categories.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No categories found for Alpha Horizontal drawer")
            drawerTabs.visibility = View.GONE
            return
        }
        
        if (categories.size > 1) {
            tabLayoutMediator = TabLayoutMediator(drawerTabs, drawerPager) { tab, position ->
                if (position < categories.size) {
                    tab.text = categories[position].toString()
                }
            }
            tabLayoutMediator?.attach()
            
            // Set up custom tab layout with centering and padding
            setupDrawerTabsLayout(categories)
        } else {
            drawerTabs.visibility = View.GONE
        }
    }
    
    private fun setupDrawerTabsLayout(categories: List<Char>) {
        // Find the HorizontalScrollView parent
        val scrollView = view?.findViewById<android.widget.HorizontalScrollView>(R.id.drawerTabsScrollView)
        
        // Set up scroll listener to keep selected tab centered
        drawerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Scroll to center the selected tab
                drawerTabs.post {
                    val selectedTab = drawerTabs.getTabAt(position)
                    selectedTab?.view?.let { tabView ->
                        scrollView?.let { scroll ->
                            // Calculate the position to scroll to center the tab
                            val tabLeft = tabView.left
                            val tabWidth = tabView.width
                            val scrollWidth = scroll.width
                            
                            // Center the tab: scroll so that tab's center aligns with scroll view's center
                            val tabCenter = tabLeft + (tabWidth / 2)
                            val scrollCenter = scrollWidth / 2
                            val scrollToX = tabCenter - scrollCenter
                            
                            scroll.smoothScrollTo(scrollToX.coerceAtLeast(0), 0)
                        }
                    }
                }
            }
        })
        
        // Also center the initial selected tab
        drawerTabs.post {
            val currentPosition = drawerPager.currentItem
            val selectedTab = drawerTabs.getTabAt(currentPosition)
            selectedTab?.view?.let { tabView ->
                scrollView?.let { scroll ->
                    val tabLeft = tabView.left
                    val tabWidth = tabView.width
                    val scrollWidth = scroll.width
                    val tabCenter = tabLeft + (tabWidth / 2)
                    val scrollCenter = scrollWidth / 2
                    val scrollToX = tabCenter - scrollCenter
                    scroll.scrollTo(scrollToX.coerceAtLeast(0), 0)
                }
            }
        }
    }
    
    // Horizontal: Horizontal pages, apps in alphabetical order but pages not segregated by letter
    private fun setupHorizontalDrawer() {
        drawerTabs.visibility = View.GONE
        showScrollHandle(false)
        
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No apps available for Horizontal drawer")
            return
        }
        
        // Sort all apps alphabetically
        val sortedApps = apps.sortedBy { it.label.lowercase() }
        
        // Calculate apps per page
        val columns = preferences.drawerGridColumns
        val rows = preferences.drawerGridRows
        val appsPerPage = if (columns > 0 && rows > 0) columns * rows else 24 // Default to 24 if invalid
        
        if (appsPerPage <= 0) {
            android.util.Log.e("AppDrawerFragment", "Invalid appsPerPage: $appsPerPage, using default")
            return
        }
        
        // Create pages
        val pages = sortedApps.chunked(appsPerPage)
        
        if (pages.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No pages created for Horizontal drawer")
            return
        }
        
        val adapter = HorizontalDrawerAdapter(this, pages, preferences)
        drawerPager.adapter = adapter
    }
    
    // List: List view with apps in alphabetical order (horizontal layout: icon left, name right)
    private fun setupListDrawer() {
        drawerTabs.visibility = View.GONE
        showScrollHandle(true)
        
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No apps available for List drawer")
            return
        }
        
        // Sort all apps alphabetically
        val sortedApps = apps.sortedBy { it.label.lowercase() }
        val adapter = ListDrawerAdapter(this, sortedApps, preferences)
        drawerPager.adapter = adapter
        
        // Set up scroll handle position tracking
        setupScrollHandleTracking()
    }
    
    // Alpha List: List view with section headers, each letter starts on new line (horizontal layout)
    private fun setupAlphaListDrawer() {
        drawerTabs.visibility = View.GONE
        showScrollHandle(true)
        
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No apps available for Alpha List drawer")
            return
        }
        
        // Group apps by first letter and sort
        val groupedApps = apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
        val categories = groupedApps.keys.sorted()
        
        // Create a list with section headers and apps
        val itemsWithHeaders = mutableListOf<DrawerItem>()
        categories.forEach { letter ->
            itemsWithHeaders.add(DrawerItem.Header(letter))
            val letterApps = (groupedApps[letter] ?: emptyList()).sortedBy { it.label.lowercase() }
            letterApps.forEach { app ->
                itemsWithHeaders.add(DrawerItem.App(app))
            }
        }
        
        if (itemsWithHeaders.isEmpty()) {
            android.util.Log.w("AppDrawerFragment", "No items to display in Alpha List drawer")
            return
        }
        
        val adapter = AlphaListDrawerAdapter(this, itemsWithHeaders, preferences)
        drawerPager.adapter = adapter
        
        // Set up scroll handle position tracking
        setupScrollHandleTracking()
    }
    
    private fun showScrollHandle(show: Boolean) {
        scrollHandle?.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun setupScrollHandleTracking() {
        // Find the RecyclerView inside the ViewPager2 and track its scroll
        view?.post {
            try {
                val recyclerView = findRecyclerViewInViewPager(drawerPager)
                recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        updateScrollHandlePosition(recyclerView)
                    }
                })
                
                // Initial position update
                recyclerView?.post {
                    updateScrollHandlePosition(recyclerView)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDrawerFragment", "Error setting up scroll handle: ${e.message}", e)
            }
        }
    }
    
    private fun findRecyclerViewInViewPager(viewPager: ViewPager2): RecyclerView? {
        for (i in 0 until viewPager.childCount) {
            val child = viewPager.getChildAt(i)
            if (child is RecyclerView) {
                return child
            }
            // Also check nested RecyclerViews
            val nested = child.findViewById<RecyclerView>(R.id.desktopGrid)
            if (nested != null) {
                return nested
            }
        }
        return null
    }
    
    private fun updateScrollHandlePosition(recyclerView: RecyclerView) {
        val handle = scrollHandle ?: return
        
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val totalItemCount = layoutManager.itemCount
        if (totalItemCount == 0) return
        
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        val visibleItemCount = lastVisiblePosition - firstVisiblePosition + 1
        
        if (visibleItemCount >= totalItemCount) {
            // All items are visible, hide scroll handle
            handle.visibility = View.GONE
            return
        }
        
        handle.visibility = View.VISIBLE
        
        // Calculate scroll position (0.0 to 1.0)
        val scrollPosition = if (totalItemCount > visibleItemCount) {
            firstVisiblePosition.toFloat() / (totalItemCount - visibleItemCount).toFloat()
        } else {
            0f
        }
        
        // Update scroll handle position
        val parent = handle.parent as? ViewGroup ?: return
        val parentHeight = parent.height
        val handleHeight = handle.height
        val maxTop = parentHeight - handleHeight
        
        val top = (scrollPosition * maxTop).coerceIn(0f, maxTop.toFloat()).toInt()
        val layoutParams = handle.layoutParams as? android.widget.FrameLayout.LayoutParams
        layoutParams?.topMargin = top
        handle.layoutParams = layoutParams
    }
}

class AppDrawerPageFragment : Fragment() {
    
    private lateinit var apps: List<AppInfo>
    private lateinit var preferences: LauncherPreferences
    private var useListLayout: Boolean = false
    private var isVerticalMode: Boolean = true // Default to vertical for safety
    
    companion object {
        fun newInstance(apps: List<AppInfo>, preferences: LauncherPreferences, useListLayout: Boolean = false, isVerticalMode: Boolean = true): AppDrawerPageFragment {
            val fragment = AppDrawerPageFragment()
            fragment.apps = apps
            fragment.preferences = preferences
            fragment.useListLayout = useListLayout
            fragment.isVerticalMode = isVerticalMode
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
        
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.desktopGrid)
        
        if (recyclerView == null) {
            android.util.Log.e("AppDrawerPageFragment", "RecyclerView not found!")
            return
        }
        
        // Safety check for empty apps list
        if (apps.isEmpty()) {
            android.util.Log.w("AppDrawerPageFragment", "No apps to display")
            recyclerView.adapter = null
            return
        }
        
        try {
            if (useListLayout) {
                // Use LinearLayoutManager for list view
                val layoutManager = LinearLayoutManager(requireContext())
                recyclerView.layoutManager = layoutManager
            } else {
                // Use GridLayoutManager for grid view
                val columns = preferences.drawerGridColumns
                val safeColumns = if (columns > 0) columns else 4 // Default to 4 if invalid
                val layoutManager = GridLayoutManager(requireContext(), safeColumns)
                recyclerView.layoutManager = layoutManager
            }
            
            val adapter = AppIconAdapter(apps.toMutableList(), preferences, requireContext(), useListLayout)
            
            // Set up drag and drop from drawer to home screen
            // This is called when drag threshold is exceeded
            adapter.onAppLongClick = { app ->
                // Menu is already closed by the adapter
                // Close drawer and start drag
                startDragFromDrawer(app, recyclerView)
            }
            
            // Set up "Add to Home Screen" callback
            adapter.onAddToHomeScreen = { app ->
                // Add app to home screen at first available position
                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.addAppToHomeScreen(app)
            }
            
            recyclerView.adapter = adapter
            
            // Set up swipe-to-close gesture on RecyclerView
            setupSwipeToCloseOnRecyclerView(recyclerView, isVerticalMode)
        } catch (e: Exception) {
            android.util.Log.e("AppDrawerPageFragment", "Error setting up drawer page: ${e.message}", e)
        }
    }
    
    private fun setupSwipeToCloseOnRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView, isVerticalMode: Boolean) {
        var startY = 0f
        var isDragging = false
        var wasScrolling = false
        var velocityTracker: android.view.VelocityTracker? = null
        var hasScrolledToTop = false
        
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                wasScrolling = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })
        
        recyclerView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    isDragging = false
                    wasScrolling = false
                    hasScrolledToTop = false
                    velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.y - startY
                    
                    if (isVerticalMode) {
                        // Vertical mode: Check if we can scroll up
                        val canScrollUp = recyclerView.canScrollVertically(-1)
                        
                        // If not at top and swiping down, scroll to top first
                        if (canScrollUp && deltaY > 20 && !wasScrolling && !hasScrolledToTop) {
                            // Scroll to top smoothly
                            recyclerView.smoothScrollToPosition(0)
                            hasScrolledToTop = true
                            return@setOnTouchListener true
                        }
                        
                        // If at top and swiping down, handle drawer close
                        if (!canScrollUp && deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        // Horizontal mode: Always close drawer on swipe down
                        if (deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val deltaY = event.y - startY
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val currentTranslation = drawerContainer.translationY
                            
                            velocityTracker?.computeCurrentVelocity(1000)
                            val velocityY = velocityTracker?.yVelocity ?: 0f
                            velocityTracker?.recycle()
                            velocityTracker = null
                            
                            val minThreshold = 80 * resources.displayMetrics.density
                            val maxThreshold = screenHeight * 0.15f
                            val threshold = minThreshold.coerceAtMost(maxThreshold)
                            val velocityThreshold = 500f
                            
                            if (currentTranslation > threshold || deltaY > threshold || velocityY > velocityThreshold) {
                                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                            } else {
                                drawerContainer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        
                        recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
                        drawerContainer?.parent?.requestDisallowInterceptTouchEvent(false)
                        isDragging = false
                        true
                    } else {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    private fun startDragFromDrawer(app: AppInfo, recyclerView: androidx.recyclerview.widget.RecyclerView) {
        // Close app drawer and show home screen
        // The drag is already started by the adapter's touch listener
        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
    }
}

// Fragment for Alpha Vertical drawer style (with section headers)
class AlphaVerticalDrawerPageFragment : Fragment() {
    
    private lateinit var items: List<DrawerItem>
    private lateinit var preferences: LauncherPreferences
    
    companion object {
        fun newInstance(items: List<DrawerItem>, preferences: LauncherPreferences): AlphaVerticalDrawerPageFragment {
            val fragment = AlphaVerticalDrawerPageFragment()
            fragment.items = items
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
        
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.desktopGrid)
        val columns = preferences.drawerGridColumns
        
        // Use GridLayoutManager with custom span size lookup for headers
        val layoutManager = GridLayoutManager(requireContext(), columns)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (items[position]) {
                    is DrawerItem.Header -> columns // Header takes full width
                    is DrawerItem.App -> 1 // App takes 1 column
                }
            }
        }
        recyclerView.layoutManager = layoutManager
        
        val adapter = AlphaVerticalRecyclerAdapter(items, preferences, requireContext())
        
        // Set up drag and drop from drawer to home screen
        adapter.onAppLongClick = { app ->
            startDragFromDrawer(app, recyclerView)
        }
        
        // Set up "Add to Home Screen" callback
        adapter.onAddToHomeScreen = { app ->
            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.addAppToHomeScreen(app)
        }
        
        recyclerView.adapter = adapter
        
        // Set up swipe-to-close gesture on RecyclerView (Alpha Vertical is vertical mode)
        setupSwipeToCloseOnRecyclerView(recyclerView, true)
    }
    
    private fun setupSwipeToCloseOnRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView, isVerticalMode: Boolean) {
        var startY = 0f
        var isDragging = false
        var wasScrolling = false
        var velocityTracker: android.view.VelocityTracker? = null
        var hasScrolledToTop = false
        
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                wasScrolling = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })
        
        recyclerView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    isDragging = false
                    wasScrolling = false
                    hasScrolledToTop = false
                    velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.y - startY
                    
                    if (isVerticalMode) {
                        // Vertical mode: Check if we can scroll up
                        val canScrollUp = recyclerView.canScrollVertically(-1)
                        
                        // If not at top and swiping down, scroll to top first
                        if (canScrollUp && deltaY > 20 && !wasScrolling && !hasScrolledToTop) {
                            // Scroll to top smoothly
                            recyclerView.smoothScrollToPosition(0)
                            hasScrolledToTop = true
                            return@setOnTouchListener true
                        }
                        
                        // If at top and swiping down, handle drawer close
                        if (!canScrollUp && deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        // Horizontal mode: Always close drawer on swipe down
                        if (deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val deltaY = event.y - startY
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val currentTranslation = drawerContainer.translationY
                            
                            velocityTracker?.computeCurrentVelocity(1000)
                            val velocityY = velocityTracker?.yVelocity ?: 0f
                            velocityTracker?.recycle()
                            velocityTracker = null
                            
                            val minThreshold = 80 * resources.displayMetrics.density
                            val maxThreshold = screenHeight * 0.15f
                            val threshold = minThreshold.coerceAtMost(maxThreshold)
                            val velocityThreshold = 500f
                            
                            if (currentTranslation > threshold || deltaY > threshold || velocityY > velocityThreshold) {
                                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                            } else {
                                drawerContainer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        
                        recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
                        drawerContainer?.parent?.requestDisallowInterceptTouchEvent(false)
                        isDragging = false
                        true
                    } else {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    private fun startDragFromDrawer(app: AppInfo, recyclerView: androidx.recyclerview.widget.RecyclerView) {
        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
    }
}

// RecyclerView Adapter for Alpha Vertical drawer with headers
class AlphaVerticalRecyclerAdapter(
    private val items: List<DrawerItem>,
    private val preferences: LauncherPreferences,
    private val context: android.content.Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val HEADER_TYPE = 0
    private val APP_TYPE = 1
    
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    var onAddToHomeScreen: ((AppInfo) -> Unit)? = null
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.Header -> HEADER_TYPE
            is DrawerItem.App -> APP_TYPE
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER_TYPE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                HeaderViewHolder(view)
            }
            APP_TYPE -> {
                // Reuse the same layout as regular app icons
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_icon, parent, false)
                AppViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DrawerItem.Header -> {
                (holder as HeaderViewHolder).bind(item.letter)
            }
            is DrawerItem.App -> {
                (holder as AppViewHolder).bind(item.appInfo)
            }
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
        
        fun bind(letter: Char) {
            textView.text = letter.toString()
            textView.textSize = 18f
            textView.setTextColor(0xFFFFFFFF.toInt())
            textView.setPadding(
                (16 * context.resources.displayMetrics.density).toInt(),
                (12 * context.resources.displayMetrics.density).toInt(),
                (16 * context.resources.displayMetrics.density).toInt(),
                (8 * context.resources.displayMetrics.density).toInt()
            )
            itemView.setBackgroundColor(0x33000000.toInt())
        }
    }
    
    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView = itemView.findViewById<android.widget.ImageView>(R.id.appIcon)
        private val labelView = itemView.findViewById<android.widget.TextView>(R.id.appLabel)
        
        fun bind(app: AppInfo) {
            iconView?.setImageDrawable(app.icon)
            labelView?.text = app.label
            
            itemView.setOnClickListener {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.let { activity ->
                        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                    }
                }
            }
            
            itemView.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }
        }
    }
}

// Fragment for Alpha List drawer style (with section headers, list layout)
class AlphaListDrawerPageFragment : Fragment() {
    
    private lateinit var items: List<DrawerItem>
    private lateinit var preferences: LauncherPreferences
    
    companion object {
        fun newInstance(items: List<DrawerItem>, preferences: LauncherPreferences): AlphaListDrawerPageFragment {
            val fragment = AlphaListDrawerPageFragment()
            fragment.items = items
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
        
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.desktopGrid)
        
        // Use LinearLayoutManager for list view
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        
        val adapter = AlphaListRecyclerAdapter(items, preferences, requireContext())
        
        // Set up drag and drop from drawer to home screen
        adapter.onAppLongClick = { app ->
            startDragFromDrawer(app, recyclerView)
        }
        
        // Set up "Add to Home Screen" callback
        adapter.onAddToHomeScreen = { app ->
            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.addAppToHomeScreen(app)
        }
        
        recyclerView.adapter = adapter
        
        // Set up swipe-to-close gesture on RecyclerView (Alpha List is vertical mode)
        setupSwipeToCloseOnRecyclerView(recyclerView, true)
    }
    
    private fun setupSwipeToCloseOnRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView, isVerticalMode: Boolean) {
        var startY = 0f
        var isDragging = false
        var wasScrolling = false
        var velocityTracker: android.view.VelocityTracker? = null
        var hasScrolledToTop = false
        
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                wasScrolling = newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            }
        })
        
        recyclerView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    isDragging = false
                    wasScrolling = false
                    hasScrolledToTop = false
                    velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.y - startY
                    
                    if (isVerticalMode) {
                        // Vertical mode: Check if we can scroll up
                        val canScrollUp = recyclerView.canScrollVertically(-1)
                        
                        // If not at top and swiping down, scroll to top first
                        if (canScrollUp && deltaY > 20 && !wasScrolling && !hasScrolledToTop) {
                            // Scroll to top smoothly
                            recyclerView.smoothScrollToPosition(0)
                            hasScrolledToTop = true
                            return@setOnTouchListener true
                        }
                        
                        // If at top and swiping down, handle drawer close
                        if (!canScrollUp && deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        // Horizontal mode: Always close drawer on swipe down
                        if (deltaY > 20 && !wasScrolling) {
                            if (!isDragging) {
                                isDragging = true
                                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                                
                                val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                                if (drawerContainer != null) {
                                    drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                val screenHeight = resources.displayMetrics.heightPixels
                                val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                                drawerContainer.translationY = translation
                                drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val deltaY = event.y - startY
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val currentTranslation = drawerContainer.translationY
                            
                            velocityTracker?.computeCurrentVelocity(1000)
                            val velocityY = velocityTracker?.yVelocity ?: 0f
                            velocityTracker?.recycle()
                            velocityTracker = null
                            
                            val minThreshold = 80 * resources.displayMetrics.density
                            val maxThreshold = screenHeight * 0.15f
                            val threshold = minThreshold.coerceAtMost(maxThreshold)
                            val velocityThreshold = 500f
                            
                            if (currentTranslation > threshold || deltaY > threshold || velocityY > velocityThreshold) {
                                (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                            } else {
                                drawerContainer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        
                        recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
                        drawerContainer?.parent?.requestDisallowInterceptTouchEvent(false)
                        isDragging = false
                        true
                    } else {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    private fun startDragFromDrawer(app: AppInfo, recyclerView: androidx.recyclerview.widget.RecyclerView) {
        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
    }
}

// RecyclerView Adapter for Alpha List drawer with headers (list layout)
class AlphaListRecyclerAdapter(
    private val items: List<DrawerItem>,
    private val preferences: LauncherPreferences,
    private val context: android.content.Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val HEADER_TYPE = 0
    private val APP_TYPE = 1
    
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    var onAddToHomeScreen: ((AppInfo) -> Unit)? = null
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.Header -> HEADER_TYPE
            is DrawerItem.App -> APP_TYPE
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER_TYPE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                HeaderViewHolder(view)
            }
            APP_TYPE -> {
                // Use list layout for apps
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_icon_list, parent, false)
                AppViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DrawerItem.Header -> {
                (holder as HeaderViewHolder).bind(item.letter)
            }
            is DrawerItem.App -> {
                (holder as AppViewHolder).bind(item.appInfo)
            }
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
        
        fun bind(letter: Char) {
            textView.text = letter.toString()
            textView.textSize = 18f
            textView.setTextColor(0xFFFFFFFF.toInt())
            textView.setPadding(
                (16 * context.resources.displayMetrics.density).toInt(),
                (12 * context.resources.displayMetrics.density).toInt(),
                (16 * context.resources.displayMetrics.density).toInt(),
                (8 * context.resources.displayMetrics.density).toInt()
            )
            itemView.setBackgroundColor(0x33000000.toInt())
        }
    }
    
    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView = itemView.findViewById<android.widget.ImageView>(R.id.appIcon)
        private val labelView = itemView.findViewById<android.widget.TextView>(R.id.appLabel)
        
        fun bind(app: AppInfo) {
            iconView?.setImageDrawable(app.icon)
            labelView?.text = app.label
            
            itemView.setOnClickListener {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.let { activity ->
                        (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
                    }
                }
            }
            
            itemView.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }
        }
    }
}

class AppDrawerPageAdapter(
    fragment: Fragment,
    private val allApps: List<AppInfo>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    private val groupedApps = allApps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
    private val categories = groupedApps.keys.sorted()
    
    override fun getItemCount(): Int = categories.size
    
    override fun createFragment(position: Int): Fragment {
        if (position < 0 || position >= categories.size) {
            android.util.Log.e("AppDrawerPageAdapter", "Invalid position: $position, categories size: ${categories.size}")
            return AppDrawerPageFragment.newInstance(emptyList(), preferences, false, false) // Alpha Horizontal is horizontal mode
        }
        val category = categories[position]
        val categoryApps = groupedApps[category] ?: emptyList()
        return AppDrawerPageFragment.newInstance(categoryApps, preferences, false, false) // Alpha Horizontal is horizontal mode
    }
}

// Adapter for Vertical drawer style (single page, continuous scroll)
class SinglePageDrawerAdapter(
    fragment: Fragment,
    private val apps: List<AppInfo>,
    private val preferences: LauncherPreferences,
    private val useListLayout: Boolean = false
) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 1
    
    override fun createFragment(position: Int): Fragment {
        return AppDrawerPageFragment.newInstance(apps, preferences, useListLayout, true) // Vertical mode
    }
}

// Adapter for Alpha Vertical drawer style (with section headers)
sealed class DrawerItem {
    data class Header(val letter: Char) : DrawerItem()
    data class App(val appInfo: AppInfo) : DrawerItem()
}

class AlphaVerticalDrawerAdapter(
    fragment: Fragment,
    private val items: List<DrawerItem>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 1
    
    override fun createFragment(position: Int): Fragment {
        return AlphaVerticalDrawerPageFragment.newInstance(items, preferences)
    }
}

// Adapter for Horizontal drawer style (pages without letter segregation)
class HorizontalDrawerAdapter(
    fragment: Fragment,
    private val pages: List<List<AppInfo>>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = pages.size
    
    override fun createFragment(position: Int): Fragment {
        if (position < 0 || position >= pages.size) {
            android.util.Log.e("HorizontalDrawerAdapter", "Invalid position: $position, pages size: ${pages.size}")
            return AppDrawerPageFragment.newInstance(emptyList(), preferences, false, false) // Horizontal mode
        }
        return AppDrawerPageFragment.newInstance(pages[position], preferences, false, false) // Horizontal mode
    }
}

// Adapter for List drawer style
class ListDrawerAdapter(
    fragment: Fragment,
    private val apps: List<AppInfo>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 1
    
    override fun createFragment(position: Int): Fragment {
        return AppDrawerPageFragment.newInstance(apps, preferences, true, true) // List mode is vertical
    }
}

// Adapter for Alpha List drawer style (with section headers, list layout)
class AlphaListDrawerAdapter(
    fragment: Fragment,
    private val items: List<DrawerItem>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 1
    
    override fun createFragment(position: Int): Fragment {
        return AlphaListDrawerPageFragment.newInstance(items, preferences)
    }
}

