package com.cykrome.launcher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

object BlurHelper {
    
    /**
     * Creates a blurred bitmap from a view
     */
    fun blurView(context: Context, view: View, radius: Float = 25f): Bitmap? {
        return try {
            // Create a bitmap from the view
            val bitmap = Bitmap.createBitmap(
                view.width,
                view.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            
            // Blur the bitmap
            blurBitmap(context, bitmap, radius)
        } catch (e: Exception) {
            android.util.Log.e("BlurHelper", "Error blurring view: ${e.message}", e)
            null
        }
    }
    
    /**
     * Blurs a bitmap using a simple approach
     * For simplicity, we'll use a semi-transparent overlay instead of actual blur
     */
    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap? {
        // Return the original bitmap - we'll use overlay for blur effect instead
        return bitmap
    }
    
    /**
     * Creates a simple blur overlay view with a blurred background effect
     */
    fun createBlurOverlay(context: Context, parent: ViewGroup): View {
        val overlay = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Use a semi-transparent dark overlay to simulate blur effect
            // You can adjust the color and opacity here
            setBackgroundColor(0xCC000000.toInt()) // Semi-transparent dark overlay (80% opacity)
            alpha = 0f
            visibility = View.GONE
            // Critical: Don't block touches when hidden
            isClickable = false
            isFocusable = false
            // Add click listener to dismiss when clicking outside (only when visible)
            setOnClickListener {
                // Do nothing - menu will handle dismissal
            }
        }
        parent.addView(overlay)
        return overlay
    }
    
    /**
     * Shows blur overlay with animation
     */
    fun showBlurOverlay(overlay: View) {
        overlay.visibility = View.VISIBLE
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }
    
    /**
     * Hides blur overlay with animation
     */
    fun hideBlurOverlay(overlay: View) {
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                overlay.visibility = View.GONE
            }
            .start()
    }
}

