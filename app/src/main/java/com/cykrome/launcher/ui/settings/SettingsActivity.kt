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
                .commit()
        } else {
            settingsFragment = supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment
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
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            setupPreferences()
        }
        
        private fun setupPreferences() {
            val prefs = LauncherPreferences(requireContext())
            
            // Home Screen
            findPreference<androidx.preference.ListPreference>("home_grid_columns")?.apply {
                summary = "${prefs.homeGridColumns} columns"
                value = prefs.homeGridColumns.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.homeGridColumns = (newValue as String).toInt()
                    summary = "${newValue} columns"
                    true
                }
            }
            
            findPreference<androidx.preference.ListPreference>("home_grid_rows")?.apply {
                summary = "${prefs.homeGridRows} rows"
                value = prefs.homeGridRows.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.homeGridRows = (newValue as String).toInt()
                    summary = "${newValue} rows"
                    true
                }
            }
            
            findPreference<androidx.preference.ListPreference>("icon_size")?.apply {
                summary = "${prefs.iconSize}%"
                value = prefs.iconSize.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.iconSize = (newValue as String).toInt()
                    summary = "${newValue}%"
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
                value = prefs.drawerStyle
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.drawerStyle = newValue as String
                    true
                }
            }
            
            // Gestures
            findPreference<androidx.preference.ListPreference>("swipe_up")?.apply {
                value = prefs.swipeUpAction
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.swipeUpAction = newValue as String
                    true
                }
            }
            
            findPreference<androidx.preference.ListPreference>("swipe_down")?.apply {
                value = prefs.swipeDownAction
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.swipeDownAction = newValue as String
                    true
                }
            }
            
            findPreference<androidx.preference.ListPreference>("double_tap")?.apply {
                value = prefs.doubleTapAction
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

