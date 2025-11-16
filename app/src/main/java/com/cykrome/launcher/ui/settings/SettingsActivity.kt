package com.cykrome.launcher.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.util.BackupRestoreHelper
import com.cykrome.launcher.util.RootHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsFragment: SettingsFragment
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        if (savedInstanceState == null) {
            settingsFragment = SettingsFragment()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, settingsFragment)
                .commitNow()
        } else {
            settingsFragment = supportFragmentManager.findFragmentById(R.id.settings) as? SettingsFragment
                ?: SettingsFragment().also {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, it)
                        .commitNow()
                }
        }
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    fun getSettingsFragment(): SettingsFragment = settingsFragment
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        private val backupFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    saveBackupFile(uri)
                }
            }
        }
        
        private val restoreFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    restoreFromFile(uri)
                }
            }
        }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Fix SharedPreferences values FIRST, before loading preferences
            fixInvalidPreferenceValues()
            
            // Now load preferences - they will use the fixed values from SharedPreferences
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // Ensure all ListPreferences have valid values set directly
            ensureListPreferenceValues()
        }
        
        private fun fixInvalidPreferenceValues() {
            try {
                val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                val editor = sharedPrefs.edit()
                var needsCommit = false
                
                // Fix home_grid_columns - convert from int to string if needed
                val columnsKey = "home_grid_columns"
                val columnsStr = sharedPrefs.getString(columnsKey, null) ?: run {
                    val intValue = sharedPrefs.getInt(columnsKey, 4)
                    val strValue = intValue.toString()
                    editor.putString(columnsKey, strValue)
                    needsCommit = true
                    strValue
                }
                
                // Validate and fix if invalid
                val validColumns = arrayOf("3", "4", "5", "6", "7", "8")
                if (!validColumns.contains(columnsStr)) {
                    editor.putString(columnsKey, "4")
                    needsCommit = true
                }
                
                // Fix home_grid_rows
                val rowsKey = "home_grid_rows"
                val rowsStr = sharedPrefs.getString(rowsKey, null) ?: run {
                    val intValue = sharedPrefs.getInt(rowsKey, 5)
                    val strValue = intValue.toString()
                    editor.putString(rowsKey, strValue)
                    needsCommit = true
                    strValue
                }
                
                val validRows = arrayOf("3", "4", "5", "6", "7", "8", "9", "10")
                if (!validRows.contains(rowsStr)) {
                    editor.putString(rowsKey, "5")
                    needsCommit = true
                }
                
                // Fix icon_size
                val iconSizeKey = "icon_size"
                val iconSizeStr = sharedPrefs.getString(iconSizeKey, null) ?: run {
                    val intValue = sharedPrefs.getInt(iconSizeKey, 100)
                    val strValue = intValue.toString()
                    editor.putString(iconSizeKey, strValue)
                    needsCommit = true
                    strValue
                }
                
                val validIconSizes = arrayOf("50", "60", "70", "80", "90", "100", "110", "120", "130", "140", "150")
                if (!validIconSizes.contains(iconSizeStr)) {
                    editor.putString(iconSizeKey, "100")
                    needsCommit = true
                }
                
                if (needsCommit) {
                    editor.commit() // Use commit() for synchronous write
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error fixing preference values: ${e.message}", e)
            }
        }
        
        private fun ensureListPreferenceValues() {
            try {
                val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                
                // Helper to safely set ListPreference value and use summary provider
                fun setListPreferenceValue(pref: androidx.preference.ListPreference, defaultValue: String, summaryFormatter: (String) -> String = { it }) {
                    try {
                        val storedValue = sharedPrefs.getString(pref.key, defaultValue) ?: defaultValue
                        
                        // Use summary provider to avoid crashes when getting entry from value
                        pref.setSummaryProvider { preference ->
                            try {
                                val listPref = preference as? androidx.preference.ListPreference
                                val currentValue = listPref?.value ?: defaultValue
                                summaryFormatter(currentValue)
                            } catch (e: Exception) {
                                summaryFormatter(defaultValue)
                            }
                        }
                        
                        // Check if entryValues is loaded
                        if (pref.entryValues == null) {
                            // entryValues not loaded yet, set a safe default
                            pref.value = defaultValue
                            return
                        }
                        
                        // Validate against entryValues
                        val isValid = pref.entryValues.any { it.toString() == storedValue }
                        val valueToSet = if (isValid) storedValue else defaultValue
                        
                        // Always set the value to ensure it's not null
                        pref.value = valueToSet
                        
                        // Also update SharedPreferences if we had to use default
                        if (!isValid && storedValue != defaultValue) {
                            sharedPrefs.edit().putString(pref.key, defaultValue).commit()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsFragment", "Error setting ${pref.key}: ${e.message}", e)
                        try {
                            pref.setSummaryProvider { summaryFormatter(defaultValue) }
                            pref.value = defaultValue
                        } catch (e2: Exception) {
                            android.util.Log.e("SettingsFragment", "Error setting default for ${pref.key}: ${e2.message}", e2)
                        }
                    }
                }
                
                // Ensure all ListPreferences have non-null values with custom summaries
                findPreference<androidx.preference.ListPreference>("home_grid_columns")?.let {
                    setListPreferenceValue(it, "4") { "${it} columns" }
                }
                
                findPreference<androidx.preference.ListPreference>("home_grid_rows")?.let {
                    setListPreferenceValue(it, "5") { "${it} rows" }
                }
                
                findPreference<androidx.preference.ListPreference>("icon_size")?.let {
                    setListPreferenceValue(it, "100") { "${it}%" }
                }
                
                findPreference<androidx.preference.ListPreference>("drawer_style")?.let {
                    setListPreferenceValue(it, "vertical") { 
                        it.replaceFirstChar { char -> char.uppercaseChar() }
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("swipe_up")?.let {
                    setListPreferenceValue(it, "app_drawer") { 
                        when(it) {
                            "app_drawer" -> "Open App Drawer"
                            "search" -> "Open Search"
                            "notifications" -> "Show Notifications"
                            "expand_notifications" -> "Expand Notifications"
                            else -> "None"
                        }
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("swipe_down")?.let {
                    setListPreferenceValue(it, "notifications") {
                        when(it) {
                            "app_drawer" -> "Open App Drawer"
                            "search" -> "Open Search"
                            "notifications" -> "Show Notifications"
                            "expand_notifications" -> "Expand Notifications"
                            else -> "None"
                        }
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("double_tap")?.let {
                    setListPreferenceValue(it, "none") {
                        when(it) {
                            "app_drawer" -> "Open App Drawer"
                            "search" -> "Open Search"
                            "notifications" -> "Show Notifications"
                            "expand_notifications" -> "Expand Notifications"
                            else -> "None"
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error ensuring preference values: ${e.message}", e)
            }
        }
        
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setupPreferences()
        }
        
        private fun setupPreferences() {
            try {
                val prefs = LauncherPreferences(requireContext())
                
                // Home Screen
                findPreference<androidx.preference.ListPreference>("home_grid_columns")?.apply {
                    val currentValue = prefs.homeGridColumns
                    summary = "${currentValue} columns"
                    
                    setOnPreferenceChangeListener { _, newValue ->
                        val intValue = (newValue as String).toInt()
                        prefs.homeGridColumns = intValue
                        // Also store as string for ListPreference
                        preferenceManager.sharedPreferences?.edit()?.putString(key, newValue as String)?.apply()
                        summary = "${intValue} columns"
                        true
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("home_grid_rows")?.apply {
                    val currentValue = prefs.homeGridRows
                    summary = "${currentValue} rows"
                    
                    setOnPreferenceChangeListener { _, newValue ->
                        val intValue = (newValue as String).toInt()
                        prefs.homeGridRows = intValue
                        preferenceManager.sharedPreferences?.edit()?.putString(key, newValue as String)?.apply()
                        summary = "${intValue} rows"
                        true
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("icon_size")?.apply {
                    val currentValue = prefs.iconSize
                    summary = "${currentValue}%"
                    
                    setOnPreferenceChangeListener { _, newValue ->
                        val intValue = (newValue as String).toInt()
                        prefs.iconSize = intValue
                        preferenceManager.sharedPreferences?.edit()?.putString(key, newValue as String)?.apply()
                        summary = "${intValue}%"
                        true
                    }
                }
                
                findPreference<androidx.preference.SwitchPreferenceCompat>("show_icon_labels")?.apply {
                    isChecked = prefs.showIconLabels
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.showIconLabels = newValue as Boolean
                        true
                    }
                }
                
                // App Drawer
                findPreference<androidx.preference.ListPreference>("drawer_style")?.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.drawerStyle = newValue as String
                        true
                    }
                }
                
                // Gestures
                findPreference<androidx.preference.ListPreference>("swipe_up")?.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.swipeUpAction = newValue as String
                        true
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("swipe_down")?.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.swipeDownAction = newValue as String
                        true
                    }
                }
                
                findPreference<androidx.preference.ListPreference>("double_tap")?.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.doubleTapAction = newValue as String
                        true
                    }
                }
                
                // Backup & Restore
                findPreference<androidx.preference.Preference>("backup_settings")?.setOnPreferenceClickListener {
                    backupSettings()
                    true
                }
                
                findPreference<androidx.preference.Preference>("restore_settings")?.setOnPreferenceClickListener {
                    restoreSettings()
                    true
                }
                
                // Hide Apps
                findPreference<androidx.preference.Preference>("hide_apps")?.setOnPreferenceClickListener {
                    val intent = Intent(requireContext(), HideAppsActivity::class.java)
                    startActivity(intent)
                    true
                }
                
                // Root Features
                setupRootPreferences()
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("SettingsFragment", "Error setting up preferences: ${e.message}", e)
            }
        }
        
        private fun setupRootPreferences() {
            val rootCategory = findPreference<androidx.preference.PreferenceCategory>("root_category")
            
            if (RootHelper.isRooted()) {
                rootCategory?.isVisible = true
                
                // Root Status
                findPreference<androidx.preference.Preference>("root_status")?.apply {
                    val hasRoot = RootHelper.hasRootAccess()
                    summary = if (hasRoot) {
                        "Root access available ✓"
                    } else {
                        "Device rooted but no root access"
                    }
                }
                
                // Grant Permissions
                findPreference<androidx.preference.Preference>("grant_permissions")?.apply {
                    isEnabled = RootHelper.hasRootAccess()
                    setOnPreferenceClickListener {
                        if (RootHelper.hasRootAccess()) {
                            val success = RootHelper.grantAllPermissions(requireContext().packageName)
                            if (success) {
                                Toast.makeText(
                                    requireContext(),
                                    "Permissions granted successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to grant permissions",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Root access not available",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                }
            } else {
                rootCategory?.isVisible = false
            }
        }
        
        private fun backupSettings() {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "CyKrome_Launcher_Backup_${getTimestamp()}.json")
            }
            backupFileLauncher.launch(intent)
        }
        
        private fun saveBackupFile(uri: Uri) {
            try {
                val tempFile = File(requireContext().cacheDir, "temp_backup.json")
                if (BackupRestoreHelper.backupSettings(requireContext(), tempFile)) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                    Toast.makeText(
                        requireContext(),
                        "Backup saved successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to create backup",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Error saving backup: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        private fun restoreSettings() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/json",
                    "application/zip",
                    "application/octet-stream"
                ))
            }
            restoreFileLauncher.launch(intent)
        }
        
        private fun restoreFromFile(uri: Uri) {
            try {
                val tempFile = File(requireContext().cacheDir, "temp_restore")
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                val result = BackupRestoreHelper.restoreSettings(requireContext(), tempFile)
                tempFile.delete()
                
                val formatName = when (result.format) {
                    BackupRestoreHelper.BackupFormat.NOVA_BACKUP -> "Nova Launcher"
                    BackupRestoreHelper.BackupFormat.CYKROME_JSON -> "CyKrome Launcher"
                }
                
                if (result.success) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Restore Successful")
                        .setMessage("Settings restored from $formatName backup file. Please restart the launcher for changes to take effect.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Restore Failed")
                        .setMessage("Failed to restore from $formatName backup: ${result.errorMessage ?: "Unknown error"}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Error restoring backup: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        private fun getTimestamp(): String {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}

