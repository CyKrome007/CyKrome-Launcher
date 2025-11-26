package com.cykrome.launcher.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.ui.fragments.AppDrawerFragment
import com.cykrome.launcher.ui.fragments.HomeScreenFragment
import com.cykrome.launcher.ui.fragments.SearchFragment
import com.cykrome.launcher.util.RootHelper
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    
    private lateinit var preferences: LauncherPreferences
    private var homeScreenFragment: HomeScreenFragment? = null
    private var appDrawerFragment: AppDrawerFragment? = null
    private var searchFragment: SearchFragment? = null
    
    private var gestureDetector: GestureDetector? = null
    private var doubleTapDetector: DoubleTapDetector? = null
    private var blurOverlay: View? = null
    
    // For drag-based drawer opening
    private var isDraggingDrawer = false
    private var drawerStartY = 0f
    private var touchStartY = 0f
    private var drawerContainer: View? = null
    private var homeScreenContainer: View? = null
    
    // For wallpaper zoom animation
    private var currentWallpaperZoom = 1.1f // Default zoom (bit zoomed)
    private val zoomedWallpaperScale = 1.1f // Zoomed in state
    private val normalWallpaperScale = 1.0f // Normal state (when notification is open)
    private var wallpaperDrawableWrapper: ZoomableDrawable? = null
    private var wallpaperZoomAnimator: android.animation.ValueAnimator? = null
    
    // Permission launchers
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_EXTERNAL_STORAGE permission: $isGranted")
        // Re-check and proceed with initialization if granted
        if (isGranted) {
            checkAndRequestStoragePermission()
        } else {
            // Still not granted, keep blocking
            showStoragePermissionBlockingOverlay()
        }
    }
    
    private val requestManageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasPermission = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("LauncherActivity", "MANAGE_EXTERNAL_STORAGE permission: $hasPermission")
            // Re-check permission and proceed with initialization if granted
            checkAndRequestStoragePermission()
        }
    }
    
    private var permissionBlockingOverlay: View? = null
    
    private val requestMediaImagesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_MEDIA_IMAGES permission: $isGranted")
        // After media images permission, request videos permission
        requestMediaVideosPermission()
    }
    
    private val requestMediaVideosPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_MEDIA_VIDEO permission: $isGranted")
        // After all media permissions, proceed with initialization
        checkAndRequestMediaPermissions()
    }
    
    private val requestPhoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_PHONE_STATE permission: $isGranted")
    }
    
    private val requestPhoneNumbersPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_PHONE_NUMBERS permission: $isGranted")
    }
    
    private val requestReadSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "READ_SMS permission: $isGranted")
    }
    
    private val requestSendSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "SEND_SMS permission: $isGranted")
    }
    
    private val requestReceiveSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("LauncherActivity", "RECEIVE_SMS permission: $isGranted")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Configure window for launcher
            configureWindow()
            
            setContentView(R.layout.activity_launcher)
            
            // Check storage permission first - block if not granted
            // Use post to ensure views are ready before showing overlay
            findViewById<ViewGroup>(R.id.rootLayout)?.post {
                checkAndRequestStoragePermission()
            } ?: run {
                // Fallback if rootLayout is null
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    checkAndRequestStoragePermission()
                }, 100)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("LauncherActivity", "Error in onCreate: ${e.message}", e)
            // Show error and finish
            try {
                android.widget.Toast.makeText(this, "Error initializing launcher: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                android.util.Log.e("LauncherActivity", "Error showing toast: ${e2.message}", e2)
            }
            finish()
        }
    }
    
    private fun initializeLauncher() {
        try {
            // Check if this launcher is set as default when launched as HOME
            val isHomeIntent = intent.categories?.contains(Intent.CATEGORY_HOME) == true
            if (isHomeIntent && !isDefaultLauncher()) {
                // If launched as HOME but not default, prompt user
                showSetDefaultLauncherDialog()
                return
            }
            
            // Create blur overlay for context menus
            val rootLayout = findViewById<ViewGroup>(R.id.rootLayout)
            blurOverlay = com.cykrome.launcher.util.BlurHelper.createBlurOverlay(this, rootLayout)
            // Ensure blur overlay is hidden and not blocking touches initially
            blurOverlay?.visibility = View.GONE
            blurOverlay?.isClickable = false
            blurOverlay?.isFocusable = false
            
            preferences = LauncherPreferences(this)
            
            // Ensure home screen container is visible
            findViewById<ViewGroup>(R.id.homeScreenContainer)?.visibility = View.VISIBLE
            
            // Load system wallpaper after view is created - use post to ensure view is ready
            findViewById<ViewGroup>(R.id.rootLayout)?.post {
                loadWallpaper()
            } ?: run {
                // Fallback if rootLayout is null (shouldn't happen, but just in case)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadWallpaper()
                }, 100)
            }
            
            // Check for root and grant permissions if available
            checkAndGrantRootPermissions()
            
            // Request other permissions (storage permission already checked and blocked if needed)
            requestAllPermissions()
            
            setupFragments()
            setupGestures()
            
            // If launched from app drawer and not default, show a reminder
            if (!isHomeIntent && !isDefaultLauncher()) {
                android.widget.Toast.makeText(
                    this,
                    "Please set CyKrome Launcher as default launcher to use it as your home screen",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("LauncherActivity", "Error in initializeLauncher: ${e.message}", e)
            try {
                android.widget.Toast.makeText(this, "Error initializing launcher: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                android.util.Log.e("LauncherActivity", "Error showing toast: ${e2.message}", e2)
            }
            finish()
        }
    }
            
    
    private fun loadWallpaper() {
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable
            
            if (wallpaperDrawable != null) {
                // Create a zoomable center-cropped version of the wallpaper
                wallpaperDrawableWrapper = ZoomableDrawable(wallpaperDrawable, zoomedWallpaperScale)
                
                // Set wallpaper as window background
                window.setBackgroundDrawable(wallpaperDrawableWrapper)
                
                // Set wallpaper on root layout
                findViewById<ViewGroup>(R.id.rootLayout)?.background = wallpaperDrawableWrapper
                
                // Setup notification drawer detection
                setupNotificationDrawerDetection()
                
                android.util.Log.d("LauncherActivity", "Wallpaper set successfully with zoom support")
            } else {
                android.util.Log.w("LauncherActivity", "Could not get wallpaper drawable")
            }
            
            // Add top padding to the whole launcher to avoid status bar overlap
            addTopPaddingToLauncher()
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error loading wallpaper: ${e.message}", e)
        }
    }
    
    private fun setupNotificationDrawerDetection() {
        try {
            val rootLayout = findViewById<ViewGroup>(R.id.rootLayout)
            rootLayout?.setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    // Only respond to notification drawer if app drawer is not open
                    val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
                    val isAppDrawerVisible = drawerContainer?.visibility == View.VISIBLE && 
                                            (drawerContainer.alpha > 0.5f)
                    
                    if (!isAppDrawerVisible) {
                        // Check if status bar is visible (notification drawer closed)
                        val statusBars = insets.getInsets(android.view.WindowInsets.Type.statusBars())
                        val isNotificationDrawerOpen = statusBars.top == 0
                        
                        animateWallpaperZoom(
                            if (isNotificationDrawerOpen) normalWallpaperScale else zoomedWallpaperScale
                        )
                    }
                }
                insets
            }
            
            // Also listen to system UI visibility changes for older Android versions
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                rootLayout?.setOnSystemUiVisibilityChangeListener { visibility ->
                    // Only respond to notification drawer if app drawer is not open
                    val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
                    val isAppDrawerVisible = drawerContainer?.visibility == View.VISIBLE && 
                                            (drawerContainer.alpha > 0.5f)
                    
                    if (!isAppDrawerVisible) {
                        val isFullscreen = (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                        animateWallpaperZoom(
                            if (isFullscreen) normalWallpaperScale else zoomedWallpaperScale
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error setting up notification drawer detection: ${e.message}", e)
        }
    }
    
    private fun animateWallpaperZoom(targetZoom: Float) {
        // Cancel any existing animation
        wallpaperZoomAnimator?.cancel()
        
        wallpaperDrawableWrapper?.let { drawable ->
            wallpaperZoomAnimator = android.animation.ValueAnimator.ofFloat(currentWallpaperZoom, targetZoom).apply {
                duration = 300 // Smooth 300ms animation
                interpolator = android.view.animation.DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val zoom = animator.animatedValue as Float
                    currentWallpaperZoom = zoom
                    drawable.setZoom(zoom)
                    // Invalidate to redraw
                    findViewById<ViewGroup>(R.id.rootLayout)?.invalidate()
                }
                
                start()
            }
        }
    }
    
    // Inner class for zoomable wallpaper drawable
    private inner class ZoomableDrawable(
        private val drawable: Drawable,
        private var zoom: Float = 1.1f
    ) : Drawable() {
        
        fun setZoom(newZoom: Float) {
            zoom = newZoom
            invalidateSelf()
        }
        
        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val intrinsicWidth = drawable.intrinsicWidth
            val intrinsicHeight = drawable.intrinsicHeight
            
            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                val viewWidth = bounds.width()
                val viewHeight = bounds.height()
                
                // Calculate scale to fill the view while maintaining aspect ratio (center crop)
                val scaleX = viewWidth.toFloat() / intrinsicWidth
                val scaleY = viewHeight.toFloat() / intrinsicHeight
                val baseScale = scaleX.coerceAtLeast(scaleY) // Use larger scale to fill
                
                // Apply zoom to the base scale
                val scale = baseScale * zoom
                
                // Calculate the scaled dimensions
                val scaledWidth = (intrinsicWidth * scale)
                val scaledHeight = (intrinsicHeight * scale)
                
                // Calculate center position
                val centerX = viewWidth / 2f
                val centerY = viewHeight / 2f
                
                // Save canvas state
                canvas.save()
                
                // Clip to bounds
                canvas.clipRect(bounds)
                
                // Translate to center
                canvas.translate(centerX, centerY)
                
                // Scale from center
                canvas.scale(scale, scale)
                
                // Translate back so drawable is centered
                canvas.translate(-intrinsicWidth / 2f, -intrinsicHeight / 2f)
                
                // Draw the drawable
                drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                drawable.draw(canvas)
                
                // Restore canvas state
                canvas.restore()
            } else {
                // Fallback: draw normally if dimensions are invalid
                drawable.setBounds(bounds)
                drawable.draw(canvas)
            }
        }
        
        override fun setAlpha(alpha: Int) {
            drawable.alpha = alpha
        }
        
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            drawable.colorFilter = colorFilter
        }
        
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int {
            return drawable.opacity
        }
        
        override fun getIntrinsicWidth(): Int {
            return drawable.intrinsicWidth
        }
        
        override fun getIntrinsicHeight(): Int {
            return drawable.intrinsicHeight
        }
    }
    
    
    fun addAppToHomeScreen(app: com.cykrome.launcher.model.AppInfo) {
        homeScreenFragment?.addAppToHomeScreen(app.packageName, app.activityName)
    }
    
    fun showBlurOverlay() {
        blurOverlay?.let {
            com.cykrome.launcher.util.BlurHelper.showBlurOverlay(it)
        }
    }
    
    fun hideBlurOverlay() {
        blurOverlay?.let {
            com.cykrome.launcher.util.BlurHelper.hideBlurOverlay(it)
        }
    }
    
    private fun addTopPaddingToLauncher() {
        try {
            val homeScreenContainer = findViewById<ViewGroup>(R.id.homeScreenContainer)
            val appDrawerContainer = findViewById<ViewGroup>(R.id.appDrawerContainer)
            val statusBarHeight = getStatusBarHeight()
            val extraPadding = (16 * resources.displayMetrics.density).toInt() // 16dp
            val totalPadding = statusBarHeight + extraPadding
            
            // Apply padding to home screen container
            homeScreenContainer?.setPadding(
                homeScreenContainer.paddingLeft,
                totalPadding,
                homeScreenContainer.paddingRight,
                homeScreenContainer.paddingBottom
            )
            
            // Apply padding to app drawer container
            appDrawerContainer?.setPadding(
                appDrawerContainer.paddingLeft,
                totalPadding,
                appDrawerContainer.paddingRight,
                appDrawerContainer.paddingBottom
            )
            
            android.util.Log.d("LauncherActivity", "Added top padding to launcher: $totalPadding px (statusBar: $statusBarHeight, extra: $extraPadding)")
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error adding top padding: ${e.message}", e)
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
    
    private fun configureWindow() {
        try {
            // Set window background to transparent BEFORE setContentView
            // This allows the system wallpaper to show through
            window.setBackgroundDrawableResource(android.R.color.transparent)
            
            // Enable edge-to-edge: allow drawing behind system bars
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            )
            
            // Use WindowCompat for proper edge-to-edge support
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Configure system bars to be transparent
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    setSystemBarsAppearance(
                        0, // No light status bars for now
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    show(android.view.WindowInsets.Type.statusBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error configuring window: ${e.message}", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Make window transparent when paused to prevent showing in recents
        try {
            // Only hide the window content, don't change background
            window.decorView.alpha = 0f
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error setting background in onPause: ${e.message}", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check storage permission when activity resumes (e.g., user returns from settings)
        checkAndRequestStoragePermission()
        // Restore window visibility when resumed
        try {
            window.decorView.alpha = 1f
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error restoring window visibility: ${e.message}", e)
        }
        // Reload wallpaper in case it changed - use post to ensure view is ready
        findViewById<ViewGroup>(R.id.rootLayout)?.post {
            loadWallpaper()
        }
        
        // Check again in case user changed default launcher
        val isHomeIntent = intent.categories?.contains(Intent.CATEGORY_HOME) == true
        if (isHomeIntent && !isDefaultLauncher()) {
            showSetDefaultLauncherDialog()
        }
    }
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    private fun showSetDefaultLauncherDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set as Default Launcher")
            .setMessage("CyKrome Launcher needs to be set as your default launcher to work properly. Would you like to set it now?")
            .setPositiveButton("Set as Default") { _, _ ->
                openLauncherSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun openLauncherSettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            } else {
                // For older Android versions
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                startActivity(Intent.createChooser(intent, "Select default launcher"))
            }
            finish()
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error opening launcher settings: ${e.message}", e)
            android.widget.Toast.makeText(this, "Please go to Settings > Apps > Default Apps > Home App and select CyKrome Launcher", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun checkAndGrantRootPermissions() {
        if (RootHelper.isRooted() && RootHelper.hasRootAccess()) {
            android.util.Log.d("LauncherActivity", "Root detected, attempting to grant permissions")
            
            // Grant QUERY_ALL_PACKAGES permission if not already granted
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    if (checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) 
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        RootHelper.grantAllPermissions(packageName)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LauncherActivity", "Could not grant permissions via root: ${e.message}")
                }
            }
        }
    }
    
    private fun requestAllPermissions() {
        // Storage permission is already checked and blocked in checkAndRequestStoragePermission()
        // Only request additional media permissions if MANAGE_EXTERNAL_STORAGE is already granted
        // On Android 11+, prioritize MANAGE_EXTERNAL_STORAGE (if not granted, don't request media permissions to avoid warnings)
        // On Android 10 and below, request READ_EXTERNAL_STORAGE
        val hasManageStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            false
        }
        
        // Only request additional media permissions if MANAGE_EXTERNAL_STORAGE is already granted
        // (MANAGE_EXTERNAL_STORAGE covers everything, so no need for additional permissions)
        if (!hasManageStorage && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            // Android 10 and below - request READ_EXTERNAL_STORAGE
            requestMediaPermissionsIfNeeded()
        }
        
        // Note: Phone and SMS permissions are not requested automatically
        // They can be requested later if needed by specific features
    }
    
    private fun requestMediaPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestMediaImagesPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below use READ_EXTERNAL_STORAGE
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
    
    /**
     * Check and request storage permission immediately on startup.
     * Shows blocking overlay and automatically opens settings if permission is not granted.
     */
    private fun checkAndRequestStoragePermission() {
        android.util.Log.d("LauncherActivity", "Checking storage permission on startup...")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasPermission = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("LauncherActivity", "MANAGE_EXTERNAL_STORAGE permission: $hasPermission")
            
            if (!hasPermission) {
                // Show blocking overlay
                showStoragePermissionBlockingOverlay()
                // Automatically open settings to request permission
                android.util.Log.d("LauncherActivity", "Opening settings to request storage permission")
                requestManageStoragePermission()
                // Don't proceed with initialization
                return
            } else {
                // Storage permission granted, now check media permissions
                android.util.Log.d("LauncherActivity", "Storage permission granted, checking media permissions")
                checkAndRequestMediaPermissions()
            }
        } else {
            // Android 10 and below - check READ_EXTERNAL_STORAGE
            val hasPermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            android.util.Log.d("LauncherActivity", "READ_EXTERNAL_STORAGE permission: $hasPermission")
            
            if (!hasPermission) {
                showStoragePermissionBlockingOverlay()
                // Automatically request permission
                android.util.Log.d("LauncherActivity", "Requesting READ_EXTERNAL_STORAGE permission")
                requestStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                // Don't proceed with initialization
                return
            } else {
                android.util.Log.d("LauncherActivity", "Storage permission granted, checking media permissions")
                checkAndRequestMediaPermissions()
            }
        }
    }
    
    /**
     * Check and request media permissions (photos and videos) on Android 13+
     */
    private fun checkAndRequestMediaPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            val hasImagesPermission = checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasVideosPermission = checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            
            android.util.Log.d("LauncherActivity", "READ_MEDIA_IMAGES permission: $hasImagesPermission")
            android.util.Log.d("LauncherActivity", "READ_MEDIA_VIDEO permission: $hasVideosPermission")
            
            if (!hasImagesPermission) {
                // Request images permission first
                android.util.Log.d("LauncherActivity", "Requesting READ_MEDIA_IMAGES permission")
                requestMediaImagesPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                return
            } else if (!hasVideosPermission) {
                // Request videos permission
                android.util.Log.d("LauncherActivity", "Requesting READ_MEDIA_VIDEO permission")
                requestMediaVideosPermission()
                return
            } else {
                // All permissions granted, proceed with initialization
                android.util.Log.d("LauncherActivity", "All permissions granted, proceeding with initialization")
                hideStoragePermissionBlockingOverlay()
                initializeLauncher()
            }
        } else {
            // Android 12 and below - storage permission covers media access
            android.util.Log.d("LauncherActivity", "Android 12 or below, storage permission covers media access")
            hideStoragePermissionBlockingOverlay()
            initializeLauncher()
        }
    }
    
    private fun requestMediaVideosPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestMediaVideosPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                // Already granted, proceed with initialization
                checkAndRequestMediaPermissions()
            }
        } else {
            // Android 12 and below - storage permission covers media access
            checkAndRequestMediaPermissions()
        }
    }
    
    /**
     * Check for MANAGE_EXTERNAL_STORAGE permission and block launcher if not granted.
     * Shows a blocking overlay that prevents using the launcher until permission is granted.
     * @return true if permission is granted, false if not granted (and blocking overlay is shown)
     */
    private fun checkStoragePermissionAndBlock(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasPermission = android.os.Environment.isExternalStorageManager()
            
            if (!hasPermission) {
                // Show blocking overlay
                showStoragePermissionBlockingOverlay()
                // Request permission
                requestManageStoragePermission()
                return false
            } else {
                // Permission granted, hide blocking overlay if it exists
                hideStoragePermissionBlockingOverlay()
                return true
            }
        } else {
            // Android 10 and below - check READ_EXTERNAL_STORAGE
            val hasPermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                showStoragePermissionBlockingOverlay()
                requestStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                return false
            } else {
                hideStoragePermissionBlockingOverlay()
                return true
            }
        }
    }
    
    private fun showStoragePermissionBlockingOverlay() {
        if (permissionBlockingOverlay != null) {
            android.util.Log.d("LauncherActivity", "Blocking overlay already showing")
            return // Already showing
        }
        
        android.util.Log.d("LauncherActivity", "Creating blocking overlay")
        val rootLayout = findViewById<ViewGroup>(R.id.rootLayout) ?: run {
            android.util.Log.e("LauncherActivity", "rootLayout is null, cannot show blocking overlay")
            return
        }
        
        // Create blocking overlay
        permissionBlockingOverlay = android.widget.FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xE0000000.toInt()) // Semi-transparent black
            isClickable = true
            isFocusable = true
        }
        
        // Create dialog content
        val dialogContent = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                setMargins(
                    (32 * resources.displayMetrics.density).toInt(),
                    0,
                    (32 * resources.displayMetrics.density).toInt(),
                    0
                )
            }
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(0xFF2A2A2A.toInt())
            elevation = 8f
        }
        
        // Title
        val title = android.widget.TextView(this).apply {
            text = "Storage Permission Required"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
        }
        
        // Message
        val message = android.widget.TextView(this).apply {
            text = "CyKrome Launcher requires full storage access to function properly. Please grant the permission to continue."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 16f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (24 * resources.displayMetrics.density).toInt()
            }
        }
        
        // Grant Permission Button
        val grantButton = android.widget.Button(this).apply {
            text = "Grant Permission"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2196F3.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                height = (48 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                requestManageStoragePermission()
            }
        }
        
        dialogContent.addView(title)
        dialogContent.addView(message)
        dialogContent.addView(grantButton)
        
        permissionBlockingOverlay?.let { overlay ->
            (overlay as? ViewGroup)?.addView(dialogContent)
            rootLayout.addView(overlay)
        }
    }
    
    private fun hideStoragePermissionBlockingOverlay() {
        permissionBlockingOverlay?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            permissionBlockingOverlay = null
        }
    }
    
    private fun requestManageStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                requestManageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    requestManageStoragePermissionLauncher.launch(intent)
                } catch (e2: Exception) {
                    android.util.Log.e("LauncherActivity", "Could not open storage permission settings: ${e2.message}", e2)
                    android.widget.Toast.makeText(
                        this,
                        "Please enable 'All files access' in Settings > Apps > CyKrome Launcher > Permissions",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            requestStoragePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    private fun setupFragments() {
        try {
            // Only create home screen fragment if it doesn't exist
            val existingFragment = supportFragmentManager.findFragmentById(R.id.homeScreenContainer)
            if (existingFragment == null) {
                homeScreenFragment = HomeScreenFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .add(R.id.homeScreenContainer, homeScreenFragment!!)
                    .commitNow()
            } else {
                homeScreenFragment = existingFragment as? HomeScreenFragment
                if (homeScreenFragment == null) {
                    // Fragment exists but is wrong type, replace it
                    homeScreenFragment = HomeScreenFragment.newInstance()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.homeScreenContainer, homeScreenFragment!!)
                        .commitNow()
                }
            }
            
            // Initialize other fragments lazily (they'll be added when needed)
            appDrawerFragment = null
            searchFragment = null
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("LauncherActivity", "Error setting up fragments: ${e.message}", e)
            android.widget.Toast.makeText(this, "Error setting up fragments: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupGestures() {
        doubleTapDetector = DoubleTapDetector()
        gestureDetector = GestureDetector(this, object : android.view.GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onShowPress(e: MotionEvent) {}
            override fun onSingleTapUp(e: MotionEvent): Boolean = false
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // Handle drawer dragging
                if (isDraggingDrawer) {
                    handleDrawerDrag(e2)
                    return true
                }
                return false
            }
            override fun onLongPress(e: MotionEvent) {}
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                // If we were dragging the drawer, handle the release
                if (isDraggingDrawer) {
                    handleDrawerRelease(velocityY)
                    return true
                }
                
                val deltaY = e2.y - e1.y
                val deltaX = e2.x - e1.x
                val absDeltaY = Math.abs(deltaY)
                val absDeltaX = Math.abs(deltaX)
                val minSwipeDistance = ViewConfiguration.get(this@LauncherActivity).scaledTouchSlop * 2
                val minSwipeVelocity = ViewConfiguration.get(this@LauncherActivity).scaledMinimumFlingVelocity
                
                // Check for vertical swipe (up or down)
                if (absDeltaY > absDeltaX && absDeltaY > minSwipeDistance && Math.abs(velocityY) > minSwipeVelocity) {
                    if (deltaY < 0 && velocityY < 0) {
                        // Swipe up
                        android.util.Log.d("LauncherActivity", "Swipe up detected")
                        handleGesture(preferences.swipeUpAction)
                        return true
                    } else if (deltaY > 0 && velocityY > 0) {
                        // Swipe down
                        android.util.Log.d("LauncherActivity", "Swipe down detected")
                        handleGesture(preferences.swipeDownAction)
                        return true
                    }
                }
                return false
            }
        })
        
        // Initialize containers
        drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
        
        // Set up touch listener on the root layout to catch all gestures
        val rootLayout = findViewById<ViewGroup>(R.id.rootLayout)
        rootLayout?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check if we should start dragging drawer
                    if (shouldStartDrawerDrag(event)) {
                        startDrawerDrag(event)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Handle drawer dragging in real-time
                    if (isDraggingDrawer) {
                        handleDrawerDrag(event)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingDrawer) {
                        // Calculate velocity for release
                        val velocityTracker = android.view.VelocityTracker.obtain()
                        velocityTracker.addMovement(event)
                        velocityTracker.computeCurrentVelocity(1000)
                        val velocityY = velocityTracker.yVelocity
                        velocityTracker.recycle()
                        handleDrawerRelease(velocityY)
                        return@setOnTouchListener true
                    }
                }
            }
            
            // Also pass to gesture detector for other gestures
            val handled = gestureDetector?.onTouchEvent(event) ?: false
            if (handled) {
                android.util.Log.d("LauncherActivity", "Gesture handled by root view")
            }
            handled
        }
        
        // Also set on content view as fallback
        findViewById<View>(android.R.id.content)?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (shouldStartDrawerDrag(event)) {
                        startDrawerDrag(event)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingDrawer) {
                        handleDrawerDrag(event)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingDrawer) {
                        val velocityTracker = android.view.VelocityTracker.obtain()
                        velocityTracker.addMovement(event)
                        velocityTracker.computeCurrentVelocity(1000)
                        val velocityY = velocityTracker.yVelocity
                        velocityTracker.recycle()
                        handleDrawerRelease(velocityY)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check if we should start dragging drawer
                    if (shouldStartDrawerDrag(it)) {
                        startDrawerDrag(it)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Handle drawer dragging in real-time
                    if (isDraggingDrawer) {
                        handleDrawerDrag(it)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingDrawer) {
                        // Calculate velocity for release
                        val velocityTracker = android.view.VelocityTracker.obtain()
                        velocityTracker.addMovement(it)
                        velocityTracker.computeCurrentVelocity(1000)
                        val velocityY = velocityTracker.yVelocity
                        velocityTracker.recycle()
                        handleDrawerRelease(velocityY)
                        return true
                    }
                }
            }
            
            val handled = gestureDetector?.onTouchEvent(it) ?: false
            doubleTapDetector?.onTouchEvent(it)
            if (handled || isDraggingDrawer) return true
        }
        return super.onTouchEvent(event)
    }
    
    private fun shouldStartDrawerDrag(event: MotionEvent): Boolean {
        // Only start dragging if:
        // 1. Drawer is not already open
        // 2. We're on the home screen (not in drawer or search)
        // 3. Touch is in the lower part of the screen (where swipe up would start)
        // 4. Search is not open
        val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        val searchContainer = findViewById<View>(R.id.searchContainer)
        
        if (drawerContainer?.visibility == View.VISIBLE && drawerContainer.alpha > 0.5f) {
            return false // Drawer is already open
        }
        
        if (searchContainer?.visibility == View.VISIBLE) {
            return false // Search is open
        }
        
        val screenHeight = resources.displayMetrics.heightPixels
        val touchY = event.y
        val startDragZone = screenHeight * 0.7f // Start drag in bottom 30% of screen
        
        return touchY > startDragZone && preferences.swipeUpAction == LauncherPreferences.ACTION_APP_DRAWER
    }
    
    private fun startDrawerDrag(event: MotionEvent) {
        isDraggingDrawer = true
        touchStartY = event.y
        
        // Ensure drawer fragment exists
        if (appDrawerFragment == null) {
            appDrawerFragment = AppDrawerFragment.newInstance()
        }
        
        val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        val homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
        
        appDrawerFragment?.let {
            if (supportFragmentManager.findFragmentById(R.id.appDrawerContainer) == null) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.appDrawerContainer, it)
                    .commitNow()
            }
        }
        
        // Initialize drawer position
        drawerContainer?.let {
            it.visibility = View.VISIBLE
            val screenHeight = resources.displayMetrics.heightPixels
            drawerStartY = screenHeight.toFloat()
            it.translationY = drawerStartY
            it.alpha = 0f
        }
        
        // Initialize home screen state
        homeScreenContainer?.let {
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }
    }
    
    private fun handleDrawerDrag(event: MotionEvent) {
        if (!isDraggingDrawer) return
        
        val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        val homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
        val screenHeight = resources.displayMetrics.heightPixels
        
        val deltaY = touchStartY - event.y // Positive when swiping up
        val newTranslationY = (drawerStartY - deltaY).coerceIn(0f, screenHeight.toFloat())
        
        drawerContainer?.translationY = newTranslationY
        
        // Calculate progress (0 = closed, 1 = open)
        val progress = 1f - (newTranslationY / screenHeight)
        
        // Update alpha based on progress
        drawerContainer?.alpha = progress
        
        // Update home screen based on progress
        homeScreenContainer?.alpha = 1f - (progress * 0.3f) // Fade out slightly
        val scale = 1f - (progress * 0.05f) // Scale down slightly
        homeScreenContainer?.scaleX = scale
        homeScreenContainer?.scaleY = scale
    }
    
    private fun handleDrawerRelease(velocityY: Float) {
        if (!isDraggingDrawer) return
        
        val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        val homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
        val screenHeight = resources.displayMetrics.heightPixels
        
        val currentTranslationY = drawerContainer?.translationY ?: screenHeight.toFloat()
        val progress = 1f - (currentTranslationY / screenHeight)
        
        // Determine if we should open or close based on progress and velocity
        val shouldOpen = progress > 0.3f || (progress > 0.1f && velocityY < -1000f)
        
        if (shouldOpen) {
            // Complete opening
            // Zoom out wallpaper smoothly
            animateWallpaperZoom(normalWallpaperScale)
            
            drawerContainer?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
            
            homeScreenContainer?.animate()
                ?.alpha(0f)
                ?.scaleX(0.95f)
                ?.scaleY(0.95f)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                ?.start()
        } else {
            // Close drawer
            // Zoom in wallpaper smoothly
            animateWallpaperZoom(zoomedWallpaperScale)
            
            drawerContainer?.animate()
                ?.alpha(0f)
                ?.translationY(screenHeight.toFloat())
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                ?.withEndAction {
                    drawerContainer?.visibility = View.GONE
                }
                ?.start()
            
            homeScreenContainer?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
        }
        
        isDraggingDrawer = false
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
        try {
            android.util.Log.d("LauncherActivity", "openAppDrawer called")
            if (appDrawerFragment == null) {
                appDrawerFragment = AppDrawerFragment.newInstance()
            }
            appDrawerFragment?.let {
                val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
                if (supportFragmentManager.findFragmentById(R.id.appDrawerContainer) == null) {
                    supportFragmentManager.beginTransaction()
                        .add(R.id.appDrawerContainer, it)
                        .commitNow()
                }
                
                // Zoom out wallpaper smoothly
                animateWallpaperZoom(normalWallpaperScale)
                
                // Hide home screen with animation
                val homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
                homeScreenContainer?.animate()?.apply {
                    alpha(0f)
                    scaleX(0.95f)
                    scaleY(0.95f)
                    duration = 300
                    interpolator = android.view.animation.AccelerateInterpolator()
                    start()
                }
                
                // Animate drawer opening - slide up from bottom
                drawerContainer.visibility = View.VISIBLE
                drawerContainer.alpha = 0f
                
                // Post to ensure view is measured
                drawerContainer.post {
                    val screenHeight = resources.displayMetrics.heightPixels
                    drawerContainer.translationY = screenHeight.toFloat()
                    
                    drawerContainer.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                
                android.util.Log.d("LauncherActivity", "App drawer opened with animation")
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error opening app drawer: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun closeAppDrawer() {
        val drawerContainer = findViewById<View>(R.id.appDrawerContainer)
        val homeScreenContainer = findViewById<View>(R.id.homeScreenContainer)
        val screenHeight = resources.displayMetrics.heightPixels
        
        // Zoom in wallpaper smoothly
        animateWallpaperZoom(zoomedWallpaperScale)
        
        drawerContainer.animate()
            .alpha(0f)
            .translationY(screenHeight.toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                drawerContainer.visibility = View.GONE
                drawerContainer.translationY = 0f
                drawerContainer.alpha = 1f
            }
            .start()
        
        // Show home screen with animation
        homeScreenContainer?.animate()?.apply {
            alpha(1f)
            scaleX(1f)
            scaleY(1f)
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
            start()
        }
    }
    
    fun focusDrawerSearch() {
        appDrawerFragment?.focusSearchInput()
    }
    
    fun openSearch() {
        try {
            if (searchFragment == null) {
                searchFragment = SearchFragment.newInstance()
            }
            searchFragment?.let {
                findViewById<View>(R.id.searchContainer).visibility = View.VISIBLE
                if (supportFragmentManager.findFragmentById(R.id.searchContainer) == null) {
                    supportFragmentManager.beginTransaction()
                        .add(R.id.searchContainer, it)
                        .commit()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Error opening search: ${e.message}", e)
        }
    }
    
    fun closeSearch() {
        findViewById<View>(R.id.searchContainer).visibility = View.GONE
    }
    
    private fun expandNotifications() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName("com.android.systemui", "com.android.systemui.statusbar.phone.StatusBar")
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
                // Do nothing when back button is pressed on home screen
                // This prevents showing recents activity
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

