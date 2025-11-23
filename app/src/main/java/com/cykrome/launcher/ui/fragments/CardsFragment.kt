package com.cykrome.launcher.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.ui.adapters.AppIconAdapter
import com.cykrome.launcher.util.AppLoader
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class CardsFragment : Fragment() {
    
    private var calendarCardEnabled = false
    private var weatherCardEnabled = false
    private var contactCardsEnabled = false
    private lateinit var preferences: LauncherPreferences
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchResults: RecyclerView
    private lateinit var lastUsedAppsTitle: TextView
    private lateinit var lastUsedAppsScrollView: View
    private var allApps: List<AppInfo> = emptyList()
    private var searchAdapter: AppIconAdapter? = null
    
    companion object {
        fun newInstance(): CardsFragment {
            return CardsFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cards, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferences = LauncherPreferences(requireContext())
        
        searchInput = view.findViewById(R.id.searchInput)
        searchResults = view.findViewById(R.id.searchResults)
        lastUsedAppsTitle = view.findViewById(R.id.lastUsedAppsTitle)
        lastUsedAppsScrollView = view.findViewById(R.id.lastUsedAppsScrollView)
        val menuButton = view.findViewById<View>(R.id.menuButton)
        val settingsButton = view.findViewById<View>(R.id.settingsButton)
        val lastUsedAppsContainer = view.findViewById<LinearLayout>(R.id.lastUsedAppsContainer)
        val calendarSwitch = view.findViewById<Switch>(R.id.calendarCardSwitch)
        val weatherSwitch = view.findViewById<Switch>(R.id.weatherCardSwitch)
        val contactCardsSwitch = view.findViewById<Switch>(R.id.contactCardsSwitch)
        val dismissButton = view.findViewById<View>(R.id.dismissButton)
        val grantPermissionsButton = view.findViewById<View>(R.id.grantPermissionsButton)
        
        // Set up search input
        setupSearchInput()
        
        // Set up menu button (3 dots) beside search bar
        menuButton?.setOnClickListener { v ->
            showMenuPopup(v)
        }
        
        // Set up settings button
        settingsButton?.setOnClickListener {
            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.openSettings()
        }
        
        // Load all apps for search
        loadAllApps()
        
        // Load last used apps
        loadLastUsedApps(lastUsedAppsContainer)
        
        // Check current permission states and update UI
        updateUIBasedOnPermissions(view)
        
        // Set up switch listeners
        calendarSwitch?.setOnCheckedChangeListener { _, isChecked ->
            calendarCardEnabled = isChecked
            if (isChecked) {
                requestCalendarPermission()
            }
        }
        
        weatherSwitch?.setOnCheckedChangeListener { _, isChecked ->
            weatherCardEnabled = isChecked
            if (isChecked) {
                requestLocationPermission()
            }
        }
        
        contactCardsSwitch?.setOnCheckedChangeListener { _, isChecked ->
            contactCardsEnabled = isChecked
            if (isChecked) {
                requestContactsPermission()
            }
        }
        
        dismissButton?.setOnClickListener {
            // Hide the welcome dialog
            view.findViewById<View>(R.id.welcomeDialog)?.visibility = View.GONE
        }
        
        grantPermissionsButton?.setOnClickListener {
            // Request all permissions
            requestAllCardPermissions()
        }
    }
    
    private fun updateUIBasedOnPermissions(view: View) {
        val hasCalendarPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        val allPermissionsGranted = hasCalendarPermission && hasLocationPermission && hasContactsPermission
        
        val welcomeDialog = view.findViewById<View>(R.id.welcomeDialog)
        val cardsContainer = view.findViewById<LinearLayout>(R.id.cardsContainer)
        
        if (allPermissionsGranted) {
            // Hide welcome dialog and show actual cards
            welcomeDialog?.visibility = View.GONE
            cardsContainer?.visibility = View.VISIBLE
            loadActualCards(cardsContainer)
        } else {
            // Show welcome dialog and hide cards
            welcomeDialog?.visibility = View.VISIBLE
            cardsContainer?.visibility = View.GONE
            updateSwitchStates(view)
        }
    }
    
    private fun loadActualCards(container: LinearLayout?) {
        if (container == null) return
        
        container.removeAllViews()
        
        val hasCalendarPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        val density = resources.displayMetrics.density
        
        // Weather Card
        if (hasLocationPermission) {
            container.addView(createWeatherCard(density))
        }
        
        // Calendar Card
        if (hasCalendarPermission) {
            container.addView(createCalendarCard(density))
        }
        
        // Contacts Card
        if (hasContactsPermission) {
            container.addView(createContactsCard(density))
        }
    }
    
    private fun createWeatherCard(density: Float): View {
        val card = android.widget.LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = 16 * density
            }
        }
        
        val title = TextView(requireContext()).apply {
            text = "Weather"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        
        val temp = TextView(requireContext()).apply {
            text = "25°C" // Placeholder - would fetch from weather API
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 32f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }
        
        val description = TextView(requireContext()).apply {
            text = "Sunny" // Placeholder
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
        }
        
        card.addView(title)
        card.addView(temp)
        card.addView(description)
        
        return card
    }
    
    private fun createCalendarCard(density: Float): View {
        val card = android.widget.LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = 16 * density
            }
        }
        
        val title = TextView(requireContext()).apply {
            text = "Calendar"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        
        val event = TextView(requireContext()).apply {
            text = "No upcoming events" // Placeholder - would fetch from calendar
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
        }
        
        card.addView(title)
        card.addView(event)
        
        return card
    }
    
    private fun createContactsCard(density: Float): View {
        val card = android.widget.LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = 16 * density
            }
        }
        
        val title = TextView(requireContext()).apply {
            text = "Favorite Contacts"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        
        val contactsList = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Placeholder contacts - would load from contacts
        for (i in 1..3) {
            val contactView = TextView(requireContext()).apply {
                text = "Contact $i"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
            }
            contactsList.addView(contactView)
        }
        
        card.addView(title)
        card.addView(contactsList)
        
        return card
    }
    
    private fun updateSwitchStates(view: View) {
        val calendarSwitch = view.findViewById<Switch>(R.id.calendarCardSwitch)
        val weatherSwitch = view.findViewById<Switch>(R.id.weatherCardSwitch)
        val contactCardsSwitch = view.findViewById<Switch>(R.id.contactCardsSwitch)
        
        // Check if permissions are already granted
        val hasCalendarPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        calendarSwitch?.isChecked = hasCalendarPermission
        weatherSwitch?.isChecked = hasLocationPermission
        contactCardsSwitch?.isChecked = hasContactsPermission
    }
    
    private fun requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 100)
        }
    }
    
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
    }
    
    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 102)
        }
    }
    
    private fun requestAllCardPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_CALENDAR)
        }
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 200)
        } else {
            Toast.makeText(requireContext(), "All permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(requireContext(), "Permissions granted", Toast.LENGTH_SHORT).show()
            view?.let { updateUIBasedOnPermissions(it) }
        } else {
            Toast.makeText(requireContext(), "Some permissions were denied", Toast.LENGTH_SHORT).show()
            view?.let { updateUIBasedOnPermissions(it) }
        }
    }
    
    private fun loadLastUsedApps(container: LinearLayout?) {
        if (container == null) return
        
        lifecycleScope.launch {
            try {
                // Load all apps
                val allApps = AppLoader.loadApps(requireContext(), preferences.hiddenApps)
                
                // For now, show first 8 apps as "last used" (in a real implementation, you'd use UsageStatsManager)
                val lastUsedApps = allApps.take(8)
                
                // Clear container
                container.removeAllViews()
                
                // Add app icons
                val density = resources.displayMetrics.density
                val iconSize = (64 * density).toInt()
                val margin = (8 * density).toInt()
                
                lastUsedApps.forEach { app ->
                    val appItem = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(margin, 0, margin, 0)
                        }
                    }
                    
                    val iconView = ImageView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(app.icon)
                    }
                    
                    val labelView = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (4 * density).toInt()
                        }
                        text = app.label
                        textSize = 10f
                        setTextColor(0xFFFFFFFF.toInt())
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = android.view.Gravity.CENTER
                        maxWidth = iconSize
                    }
                    
                    appItem.addView(iconView)
                    appItem.addView(labelView)
                    
                    appItem.setOnClickListener {
                        try {
                            val intent = app.getLaunchIntent(requireContext().packageManager)
                            intent?.let { startActivity(it) }
                        } catch (e: Exception) {
                            android.util.Log.e("CardsFragment", "Error launching app: ${e.message}", e)
                        }
                    }
                    
                    container.addView(appItem)
                }
            } catch (e: Exception) {
                android.util.Log.e("CardsFragment", "Error loading last used apps: ${e.message}", e)
            }
        }
    }
    
    private fun setupSearchInput() {
        // Set up search results RecyclerView
        searchResults.layoutManager = GridLayoutManager(requireContext(), 4)
        
        // Set up text change listener
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Show keyboard when search input is focused
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        
        // Request focus when clicked
        searchInput.setOnClickListener {
            searchInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun loadAllApps() {
        lifecycleScope.launch {
            try {
                allApps = AppLoader.loadApps(requireContext(), preferences.hiddenApps)
            } catch (e: Exception) {
                android.util.Log.e("CardsFragment", "Error loading apps: ${e.message}", e)
            }
        }
    }
    
    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            // Hide search results, show last used apps
            searchResults.visibility = View.GONE
            lastUsedAppsTitle.visibility = View.VISIBLE
            lastUsedAppsScrollView.visibility = View.VISIBLE
        } else {
            // Show search results, hide last used apps
            val filtered = allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
            
            searchAdapter = AppIconAdapter(filtered.toMutableList(), preferences, requireContext())
            searchResults.adapter = searchAdapter
            searchResults.visibility = View.VISIBLE
            lastUsedAppsTitle.visibility = View.GONE
            lastUsedAppsScrollView.visibility = View.GONE
        }
    }
    
    private fun showMenuPopup(anchor: View) {
        val popupMenu = android.widget.PopupMenu(requireContext(), anchor)
        popupMenu.menuInflater.inflate(R.menu.cards_menu, popupMenu.menu)
        
        // Style the popup menu to match the image (dark gray background, white text)
        try {
            val popup = popupMenu.javaClass.getDeclaredField("mPopup")
            popup.isAccessible = true
            val menuPopup = popup.get(popupMenu)
            menuPopup?.javaClass?.getDeclaredMethod("setForceShowIcon", Boolean::class.java)?.invoke(menuPopup, false)
            
            // Set background color
            val listView = menuPopup?.javaClass?.getDeclaredMethod("getListView")?.invoke(menuPopup) as? android.widget.ListView
            listView?.setBackgroundColor(0xFF3A3A3A.toInt())
        } catch (e: Exception) {
            android.util.Log.d("CardsFragment", "Could not style popup menu: ${e.message}")
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.addCard -> {
                    // TODO: Implement add card functionality
                    Toast.makeText(requireContext(), "Add card", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.apiIntegrations -> {
                    // TODO: Implement API integrations
                    Toast.makeText(requireContext(), "API Integrations", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.settings -> {
                    (activity as? com.cykrome.launcher.ui.LauncherActivity)?.openSettings()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
}

