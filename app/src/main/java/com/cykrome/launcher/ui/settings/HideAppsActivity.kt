package com.cykrome.launcher.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.ui.adapters.HideAppsAdapter
import com.cykrome.launcher.util.AppLoader
import kotlinx.coroutines.launch

class HideAppsActivity : AppCompatActivity() {
    
    private lateinit var preferences: LauncherPreferences
    private lateinit var adapter: HideAppsAdapter
    private var allApps: List<AppInfo> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hide_apps)
        
        preferences = LauncherPreferences(this)
        
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.hideAppsList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = HideAppsAdapter(preferences.hiddenApps.toMutableSet()) { packageName, isHidden ->
            val hiddenSet = preferences.hiddenApps.toMutableSet()
            if (isHidden) {
                hiddenSet.add(packageName)
            } else {
                hiddenSet.remove(packageName)
            }
            preferences.hiddenApps = hiddenSet
        }
        
        recyclerView.adapter = adapter
        
        loadApps()
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            allApps = AppLoader.loadApps(this@HideAppsActivity, emptySet())
            adapter.updateApps(allApps)
        }
    }
}

