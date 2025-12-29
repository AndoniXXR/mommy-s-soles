package com.e621.client.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Custom SwipeRefreshLayout that only intercepts vertical swipes.
 * This prevents conflicts with horizontal ViewPager2 swiping.
 * 
 * Based on original app's CustomSwipeRefreshLayout implementation.
 */
class CustomSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var declined: Boolean = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                declined = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (declined) {
                    return false
                }
                
                val diffX = abs(event.x - initialX)
                val diffY = abs(event.y - initialY)
                
                // If horizontal movement is greater than vertical, don't intercept
                // This allows ViewPager2 to handle the horizontal swipe
                if (diffX > touchSlop && diffX > diffY) {
                    declined = true
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }
}
