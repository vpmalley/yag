package fr.vpm.yag.ui.dashboard

import android.content.Context
import android.view.ScaleGestureDetector
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable gesture listener for pinch-to-zoom column count adjustment.
 * Callbacks are invoked when pinch gestures change the column count.
 */
class ColumnCountGestureListener(
    private val context: Context,
    private val minColumns: Int = 1,
    private val maxColumns: Int = 6,
    private val onColumnCountChanged: (Int) -> Unit
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    
    private var currentColumns: Int = 3
    
    fun setCurrentColumns(columns: Int) {
        currentColumns = min(max(columns, minColumns), maxColumns)
    }
    
    fun getCurrentColumns(): Int = currentColumns
    
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        
        // Invert scale: pinch out (zoom in) = decrease columns, pinch in (zoom out) = increase columns
        if (scaleFactor > 1.05f) {
            // Zoom out gesture (pinch in) - increase columns
            currentColumns = min(currentColumns + 1, maxColumns)
            onColumnCountChanged(currentColumns)
        } else if (scaleFactor < 0.95f) {
            // Zoom in gesture (pinch out) - decrease columns
            currentColumns = max(currentColumns - 1, minColumns)
            onColumnCountChanged(currentColumns)
        }
        return true
    }
    
    companion object {
        fun create(
            context: Context,
            minColumns: Int = 1,
            maxColumns: Int = 6,
            initialColumns: Int = 3,
            onColumnCountChanged: (Int) -> Unit
        ): ScaleGestureDetector {
            val listener = ColumnCountGestureListener(context, minColumns, maxColumns, onColumnCountChanged)
            listener.setCurrentColumns(initialColumns)
            return ScaleGestureDetector(context, listener)
        }
    }
}
