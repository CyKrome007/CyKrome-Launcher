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
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class AppDrawerFragment : Fragment() {
    
    private lateinit var drawerPager: ViewPager2
    private lateinit var drawerTabs: com.google.android.material.tabs.TabLayout
    private lateinit var preferences: LauncherPreferences
    private var apps: List<AppInfo> = emptyList()
    
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
        
        // Add top padding to AppBarLayout to account for status bar
        adjustDrawerTopPadding(view)
        
        loadApps()
        
        // Set up swipe down to close drawer
        setupSwipeToClose()
        
        // Close drawer on outside click (only if clicking on empty space, not on apps)
        // Removed to prevent accidental closes
    }
    
    private fun adjustDrawerTopPadding(view: View) {
        try {
            val appBarLayout = view.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
            if (appBarLayout != null) {
                val statusBarHeight = getStatusBarHeight()
                val extraPadding = (16 * resources.displayMetrics.density).toInt() // 16dp
                val totalTopPadding = statusBarHeight + extraPadding
                
                // Set padding on AppBarLayout
                appBarLayout.setPadding(
                    appBarLayout.paddingLeft,
                    totalTopPadding,
                    appBarLayout.paddingRight,
                    appBarLayout.paddingBottom
                )
                
                android.util.Log.d("AppDrawerFragment", "Added top padding to drawer: $totalTopPadding px")
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
        
        // Helper function to handle swipe down gesture
        val handleSwipeDown = { v: View, event: android.view.MotionEvent, isFromAppBar: Boolean ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Only start tracking if drawer is at top
                    if (!drawerPager.canScrollVertically(-1)) {
                        startY = event.y
                        isDragging = false
                        false
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - startY
                    // Only trigger if swiping down and drawer content is at top
                    if (deltaY > 20 && !drawerPager.canScrollVertically(-1)) {
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
                            
                            // If swiped down enough (30% of screen or 200px), close drawer
                            val threshold = (screenHeight * 0.3f).coerceAtLeast(200 * resources.displayMetrics.density)
                            if (currentTranslation > threshold || deltaY > threshold) {
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
                        false
                    }
                }
                else -> false
            }
        }
        
        // Set up swipe down gesture on AppBarLayout (TabLayout area)
        appBarLayout?.setOnTouchListener { v, event ->
            handleSwipeDown(v, event, true)
        }
        
        // Also set up swipe down on ViewPager2 when at top
        drawerPager.setOnTouchListener { v, event ->
            // Only handle if drawer is at top and swiping down
            if (!drawerPager.canScrollVertically(-1)) {
                handleSwipeDown(v, event, false)
            } else {
                false // Let ViewPager2 handle normal scrolling
            }
        }
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            apps = AppLoader.loadApps(requireContext(), preferences.hiddenApps)
            setupDrawer()
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
        } else {
            drawerTabs.visibility = View.GONE
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

