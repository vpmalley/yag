package fr.vpm.yag.ui.album

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView

/**
 * Changes the grid column count with a pinch gesture: pinching out (zoom in) shows fewer,
 * bigger photos, pinching in (zoom out) shows more, smaller photos.
 * Only takes over the gesture once a second finger is down, so single-finger scrolling,
 * taps and long-presses are left to the RecyclerView and its items.
 */
class PinchToResizeTouchListener(
    context: Context,
    private val minColumns: Int,
    private val maxColumns: Int,
    private val getColumnCount: () -> Int,
    private val onColumnCountChanged: (Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    // Scale accumulated since the last column change, so a single pinch can step several times
    private var accumulatedScale = 1f

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                accumulatedScale = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                accumulatedScale *= detector.scaleFactor
                val current = getColumnCount()
                val newCount = when {
                    accumulatedScale > SCALE_STEP -> current - 1
                    accumulatedScale < 1 / SCALE_STEP -> current + 1
                    else -> return true
                }.coerceIn(minColumns, maxColumns)
                accumulatedScale = 1f
                if (newCount != current) {
                    onColumnCountChanged(newCount)
                }
                return true
            }
        }
    ).apply {
        // Double-tap-and-drag would otherwise resize the grid while the user scrolls
        isQuickScaleEnabled = false
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(e)
        return e.pointerCount > 1
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        scaleGestureDetector.onTouchEvent(e)
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    companion object {
        private const val SCALE_STEP = 1.25f
    }
}
