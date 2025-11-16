package com.cykrome.launcher.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cykrome.launcher.R
import com.cykrome.launcher.data.LauncherPreferences
import com.cykrome.launcher.model.AppInfo
import com.cykrome.launcher.ui.adapters.AppIconAdapter
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
    
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchResults: androidx.recyclerview.widget.RecyclerView
    private lateinit var preferences: LauncherPreferences
    private var allApps: List<AppInfo> = emptyList()
    private var adapter: AppIconAdapter? = null
    
    companion object {
        fun newInstance(): SearchFragment {
            return SearchFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferences = LauncherPreferences(requireContext())
        searchInput = view.findViewById(R.id.searchInput)
        searchResults = view.findViewById(R.id.searchResults)
        
        searchResults.layoutManager = LinearLayoutManager(requireContext())
        
        // Load all apps
        CoroutineScope(Dispatchers.Main).launch {
            allApps = com.cykrome.launcher.util.AppLoader.loadApps(
                requireContext(),
                preferences.hiddenApps
            )
        }
        
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Close on outside click
        view.setOnClickListener {
            (activity as? com.cykrome.launcher.ui.LauncherActivity)?.closeSearch()
        }
        
        searchInput.requestFocus()
    }
    
    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) {
            emptyList()
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        
        adapter = AppIconAdapter(filtered.toMutableList(), preferences, requireContext())
        searchResults.adapter = adapter
    }
}

