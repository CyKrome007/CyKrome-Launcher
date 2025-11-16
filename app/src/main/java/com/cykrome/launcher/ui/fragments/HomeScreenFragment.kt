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
import com.cykrome.launcher.ui.adapters.DesktopPageAdapter
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
        
        preferences = LauncherPreferences(requireContext())
        homePager = view.findViewById(R.id.homePager)
        
        loadApps()
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            apps = AppLoader.loadApps(requireContext(), preferences.hiddenApps)
            setupHomePager()
        }
    }
    
    private fun setupHomePager() {
        val adapter = DesktopPageAdapter(this, apps, preferences)
        homePager.adapter = adapter
        
        // Set page transformer for scroll effects
        when (preferences.scrollEffect) {
            LauncherPreferences.SCROLL_EFFECT_CUBE -> {
                homePager.setPageTransformer { page, position ->
                    page.rotationY = -position * 90
                }
            }
            LauncherPreferences.SCROLL_EFFECT_CYLINDER -> {
                homePager.setPageTransformer { page, position ->
                    page.rotationY = -position * 45
                    page.scaleX = 1 - Math.abs(position) * 0.2f
                    page.scaleY = 1 - Math.abs(position) * 0.2f
                }
            }
            LauncherPreferences.SCROLL_EFFECT_CAROUSEL -> {
                homePager.setPageTransformer { page, position ->
                    val absPosition = Math.abs(position)
                    page.scaleX = 1 - absPosition * 0.3f
                    page.scaleY = 1 - absPosition * 0.3f
                    page.alpha = 1 - absPosition
                }
            }
        }
    }
    
    fun refreshApps() {
        loadApps()
    }
}

class DesktopPageFragment : Fragment() {
    
    private lateinit var apps: List<AppInfo>
    private lateinit var preferences: LauncherPreferences
    
    companion object {
        fun newInstance(apps: List<AppInfo>, preferences: LauncherPreferences): DesktopPageFragment {
            val fragment = DesktopPageFragment()
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
        val columns = preferences.homeGridColumns
        val layoutManager = GridLayoutManager(requireContext(), columns)
        recyclerView.layoutManager = layoutManager
        
        val adapter = AppIconAdapter(apps, preferences, requireContext())
        recyclerView.adapter = adapter
    }
}

class DesktopPageAdapter(
    fragment: Fragment,
    private val allApps: List<AppInfo>,
    private val preferences: LauncherPreferences
) : FragmentStateAdapter(fragment) {
    
    private val itemsPerPage = preferences.homeGridColumns * preferences.homeGridRows
    
    override fun getItemCount(): Int {
        return (allApps.size + itemsPerPage - 1) / itemsPerPage
    }
    
    override fun createFragment(position: Int): Fragment {
        val start = position * itemsPerPage
        val end = minOf(start + itemsPerPage, allApps.size)
        val pageApps = allApps.subList(start, end)
        return DesktopPageFragment.newInstance(pageApps, preferences)
    }
}

