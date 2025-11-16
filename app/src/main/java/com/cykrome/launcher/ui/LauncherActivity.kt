package com.cykrome.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.ui.fragments.AppDrawerFragment
import com.cykrome.launcher.ui.fragments.HomeScreenFragment
import com.cykrome.launcher.ui.fragments.SearchFragment
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    
    private lateinit var preferences: LauncherPreferences
    private var homeScreenFragment: HomeScreenFragment? = null
    private var appDrawerFragment: AppDrawerFragment? = null
    private var searchFragment: SearchFragment? = null
    
    private var gestureDetector: GestureDetector? = null
    private var doubleTapDetector: DoubleTapDetector? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        
        preferences = LauncherPreferences(this)
        
        setupFragments()
        setupGestures()
    }
    
    private fun setupFragments() {
        homeScreenFragment = HomeScreenFragment.newInstance()
        appDrawerFragment = AppDrawerFragment.newInstance()
        searchFragment = SearchFragment.newInstance()
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.homeScreenContainer, homeScreenFragment!!)
            .commit()
    }
    
    private fun setupGestures() {
        doubleTapDetector = DoubleTapDetector()
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                e1?.let { start ->
                    e2?.let { end ->
                        val deltaY = end.y - start.y
                        val deltaX = end.x - start.x
                        val absDeltaY = Math.abs(deltaY)
                        val absDeltaX = Math.abs(deltaX)
                        
                        if (absDeltaY > absDeltaX && absDeltaY > ViewConfiguration.get(this@LauncherActivity).scaledTouchSlop) {
                            if (deltaY < 0) {
                                // Swipe up
                                handleGesture(preferences.swipeUpAction)
                            } else {
                                // Swipe down
                                handleGesture(preferences.swipeDownAction)
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })
    }
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        gestureDetector?.onTouchEvent(event)
        doubleTapDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
    
    private fun handleGesture(action: String) {
        when (action) {
            LauncherPreferences.ACTION_APP_DRAWER -> openAppDrawer()
            LauncherPreferences.ACTION_SEARCH -> openSearch()
            LauncherPreferences.ACTION_NOTIFICATIONS -> expandNotifications()
            LauncherPreferences.ACTION_EXPAND_NOTIFICATIONS -> expandNotifications()
        }
    }
    
    fun openAppDrawer() {
        appDrawerFragment?.let {
            findViewById<View>(R.id.appDrawerContainer).visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .replace(R.id.appDrawerContainer, it)
                .commit()
        }
    }
    
    fun closeAppDrawer() {
        findViewById<View>(R.id.appDrawerContainer).visibility = View.GONE
    }
    
    fun openSearch() {
        searchFragment?.let {
            findViewById<View>(R.id.searchContainer).visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .replace(R.id.searchContainer, it)
                .commit()
        }
    }
    
    fun closeSearch() {
        findViewById<View>(R.id.searchContainer).visibility = View.GONE
    }
    
    private fun expandNotifications() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                className = "com.android.systemui/.statusbar.phone.StatusBar"
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to notification panel
        }
    }
    
    fun openSettings() {
        val intent = Intent(this, com.cykrome.launcher.ui.settings.SettingsActivity::class.java)
        startActivity(intent)
    }
    
    override fun onBackPressed() {
        when {
            findViewById<View>(R.id.searchContainer).visibility == View.VISIBLE -> {
                closeSearch()
            }
            findViewById<View>(R.id.appDrawerContainer).visibility == View.VISIBLE -> {
                closeAppDrawer()
            }
            else -> {
                // Don't exit launcher on back press
                moveTaskToBack(true)
            }
        }
    }
    
    inner class DoubleTapDetector {
        private var lastTapTime: Long = 0
        private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        
        fun onTouchEvent(event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    handleGesture(preferences.doubleTapAction)
                    lastTapTime = 0
                    return true
                }
                lastTapTime = currentTime
            }
            return false
        }
    }
}

