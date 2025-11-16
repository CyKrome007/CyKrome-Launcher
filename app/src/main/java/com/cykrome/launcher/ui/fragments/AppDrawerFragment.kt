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
        
        loadApps()
        
        // Close drawer on outside click
        view.setOnClickListener {
            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeAppDrawer()
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
        
        val adapter = AppIconAdapter(apps, preferences, requireContext())
        recyclerView.adapter = adapter
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

