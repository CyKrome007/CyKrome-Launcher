package com.cykrome.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

class SwipeDownInterceptingFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private var swipeDownStartY = 0f
    private var swipeDownStartX = 0f
    private var isTrackingSwipeDown = false
    private var onSwipeDownListener: (() -> Unit)? = null
    
    fun setOnSwipeDownListener(listener: (() -> Unit)?) {
        this.onSwipeDownListener = listener
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) {
            return super.onInterceptTouchEvent(ev)
        }
        
        val event = ev
        Log.d("SwipeDownIntercept", "onInterceptTouchEvent: action=${event.action}, x=${event.x}, y=${event.y}")
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownStartY = event.y
                swipeDownStartX = event.x
                isTrackingSwipeDown = true
                Log.d("SwipeDownIntercept", "Started tracking swipe down")
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTrackingSwipeDown) {
                    val deltaY = event.y - swipeDownStartY
                    val deltaX = Math.abs(event.x - swipeDownStartX)
                    val absDeltaY = Math.abs(deltaY)
                    val minSwipeDistance = ViewConfiguration.get(context).scaledTouchSlop * 2
                    
                    Log.d("SwipeDownIntercept", "MOVE: deltaY=$deltaY, deltaX=$deltaX, absDeltaY=$absDeltaY, min=$minSwipeDistance")
                    
                    // Check if it's a vertical swipe down
                    if (deltaY > 0 && absDeltaY > deltaX && absDeltaY > minSwipeDistance) {
                        Log.d("SwipeDownIntercept", "Swipe down detected! Intercepting touch events")
                        isTrackingSwipeDown = false
                        onSwipeDownListener?.invoke()
                        return true // Intercept to prevent child views from handling
                    }
                    
                    // If horizontal movement is too much, cancel tracking
                    if (deltaX > absDeltaY) {
                        isTrackingSwipeDown = false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTrackingSwipeDown = false
            }
            else -> {
                // Other actions - do nothing
            }
        }
        
        return super.onInterceptTouchEvent(ev)
    }
}

