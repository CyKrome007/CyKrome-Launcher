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
        when (preferences.drawerStyle) {
            LauncherPreferences.DRAWER_STYLE_VERTICAL -> {
                setupVerticalDrawer()
            }
            LauncherPreferences.DRAWER_STYLE_HORIZONTAL -> {
                setupHorizontalDrawer()
            }
            LauncherPreferences.DRAWER_STYLE_LIST -> {
                setupListDrawer()
            }
        }
    }
    
    private fun setupVerticalDrawer() {
        val adapter = AppDrawerPageAdapter(this, apps, preferences)
        drawerPager.adapter = adapter
        
        // Group apps by first letter
        val groupedApps = apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
        val categories = groupedApps.keys.sorted()
        
        if (categories.size > 1) {
            TabLayoutMediator(drawerTabs, drawerPager) { tab, position ->
                tab.text = categories[position].toString()
            }.attach()
            
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
    
    private fun setupHorizontalDrawer() {
        // Similar to vertical but with horizontal scrolling
        setupVerticalDrawer()
    }
    
    private fun setupListDrawer() {
        // List view implementation
        setupVerticalDrawer()
    }
}

class AppDrawerPageFragment : Fragment() {
    
    private lateinit var apps: List<AppInfo>
    private lateinit var preferences: LauncherPreferences
    
    companion object {
        fun newInstance(apps: List<AppInfo>, preferences: LauncherPreferences): AppDrawerPageFragment {
            val fragment = AppDrawerPageFragment()
            fragment.apps = apps
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
        val layoutManager = GridLayoutManager(requireContext(), columns)
        recyclerView.layoutManager = layoutManager
        
        val adapter = AppIconAdapter(apps.toMutableList(), preferences, requireContext())
        
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
        setupSwipeToCloseOnRecyclerView(recyclerView)
    }
    
    private fun setupSwipeToCloseOnRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        var startY = 0f
        var isDragging = false
        var wasScrolling = false
        var velocityTracker: android.view.VelocityTracker? = null
        
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
                    velocityTracker = android.view.VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    // Check if RecyclerView is at top - if so, allow swipe down to close
                    if (!recyclerView.canScrollVertically(-1)) {
                        // Allow parent to intercept if swiping down
                        false
                    } else {
                        false // Let RecyclerView handle scrolling
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.y - startY
                    
                    // If RecyclerView is at top and swiping down, handle drawer close
                    if (!recyclerView.canScrollVertically(-1) && deltaY > 20 && !wasScrolling) {
                        if (!isDragging) {
                            isDragging = true
                            // Prevent RecyclerView from scrolling
                            recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                            
                            // Get the drawer container from activity
                            val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                            if (drawerContainer != null) {
                                drawerContainer.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        
                        // Apply translation to drawer container
                        val drawerContainer = (activity as? com.cykrome.launcher.ui.LauncherActivity)?.findViewById<View>(R.id.appDrawerContainer)
                        if (drawerContainer != null) {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val translation = (deltaY * 0.9f).coerceAtLeast(0f).coerceAtMost(screenHeight.toFloat())
                            drawerContainer.translationY = translation
                            drawerContainer.alpha = 1f - (translation / screenHeight).coerceIn(0f, 0.5f)
                        }
                        true // Consume the event
                    } else {
                        // Let RecyclerView handle normal scrolling
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

class AppDrawerPageAdapter(
    fragment: Fragment,
    private val allApps: List<AppInfo>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    private val groupedApps = allApps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
    private val categories = groupedApps.keys.sorted()
    
    override fun getItemCount(): Int = categories.size
    
    override fun createFragment(position: Int): Fragment {
        val category = categories[position]
        val categoryApps = groupedApps[category] ?: emptyList()
        return AppDrawerPageFragment.newInstance(categoryApps, preferences)
    }
}

