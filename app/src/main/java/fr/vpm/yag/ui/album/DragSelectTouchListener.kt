package fr.vpm.yag.ui.album

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * Lets the user extend a selection by dragging across the grid after a long-press.
 *
 * Call [startDragSelection] from the long-click handler: the listener then takes over the
 * ongoing gesture, reports the range between the long-pressed item and the item under the
 * finger, and auto-scrolls when the finger nears the top or bottom edge of the grid.
 * When not drag-selecting, events are left untouched so the grid scrolls normally.
 */
class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val onRangeChanged: (anchorPosition: Int, currentPosition: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isActive = false
    private var anchorPosition = RecyclerView.NO_POSITION
    private var currentPosition = RecyclerView.NO_POSITION
    private var lastX = 0f
    private var lastY = 0f

    private val density = recyclerView.resources.displayMetrics.density
    private val autoScrollEdge = 64 * density
    private val maxAutoScrollSpeed = 24 * density
    private var autoScrollVelocity = 0
    private var isAutoScrolling = false

    private val autoScroller = object : Runnable {
        override fun run() {
            if (!isActive || autoScrollVelocity == 0) {
                isAutoScrolling = false
                return
            }
            recyclerView.scrollBy(0, autoScrollVelocity)
            updateSelection(lastX, lastY)
            recyclerView.postOnAnimation(this)
        }
    }

    fun startDragSelection(position: Int) {
        isActive = true
        anchorPosition = position
        currentPosition = position
        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        // A long-click that didn't come from a touch (e.g. accessibility) must not hijack the next gesture
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            stop()
            return false
        }
        if (!isActive) return false
        handleEvent(e)
        return true
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        handleEvent(e)
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun handleEvent(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = e.x
                lastY = e.y
                updateSelection(e.x, e.y)
                updateAutoScroll(e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
        }
    }

    private fun updateSelection(x: Float, y: Float) {
        // Clamp to the grid bounds so dragging above/below it still targets the edge row
        val clampedY = y.coerceIn(0f, (recyclerView.height - 1).toFloat())
        val child = recyclerView.findChildViewUnder(x, clampedY) ?: return
        val position = recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION || position == currentPosition) return
        currentPosition = position
        onRangeChanged(anchorPosition, position)
    }

    private fun updateAutoScroll(y: Float) {
        val height = recyclerView.height.toFloat()
        val velocity = when {
            y < autoScrollEdge ->
                -maxAutoScrollSpeed * (autoScrollEdge - y.coerceAtLeast(0f)) / autoScrollEdge
            y > height - autoScrollEdge ->
                maxAutoScrollSpeed * (y.coerceAtMost(height) - (height - autoScrollEdge)) / autoScrollEdge
            else -> 0f
        }
        autoScrollVelocity = velocity.toInt()
        if (autoScrollVelocity != 0 && !isAutoScrolling) {
            isAutoScrolling = true
            recyclerView.postOnAnimation(autoScroller)
        }
    }

    private fun stop() {
        isActive = false
        anchorPosition = RecyclerView.NO_POSITION
        currentPosition = RecyclerView.NO_POSITION
        autoScrollVelocity = 0
        isAutoScrolling = false
        recyclerView.removeCallbacks(autoScroller)
    }
}
