package com.cykrome.launcher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cykrome.launcher.R
import com.cykrome.launcher.model.AppInfo

class HideAppsAdapter(
    private val hiddenApps: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<HideAppsAdapter.HideAppViewHolder>() {
    
    private var apps: List<AppInfo> = emptyList()
    
    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HideAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hide_app, parent, false)
        return HideAppViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: HideAppViewHolder, position: Int) {
        holder.bind(apps[position])
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class HideAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appLabel: TextView = itemView.findViewById(R.id.appLabel)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        
        fun bind(app: AppInfo) {
            appIcon.setImageDrawable(app.icon)
            appLabel.text = app.label
            checkBox.isChecked = hiddenApps.contains(app.packageName)
            
            itemView.setOnClickListener {
                val isHidden = !checkBox.isChecked
                checkBox.isChecked = isHidden
                onToggle(app.packageName, isHidden)
            }
            
            checkBox.setOnClickListener {
                onToggle(app.packageName, checkBox.isChecked)
            }
        }
    }
}

