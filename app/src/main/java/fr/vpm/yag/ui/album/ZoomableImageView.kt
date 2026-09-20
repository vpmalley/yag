package fr.vpm.yag.ui.album

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // Matrix for handling zoom and pan transformations
    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    
    // Points for handling touch events
    private val startPoint = PointF()
    private val midPoint = PointF()
    private var oldDist = 1f
    private var d = 0f
    private var newRot = 0f
    private var lastEvent: FloatArray? = null

    // Gesture detectors
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // Zoom state
    private var currentScale = 1f
    private var maxScale = 4f
    private var minScale = 1f
    private var isZoomed = false

    // Double tap state
    private var lastTapTime = 0L
    private var tapCount = 0

    // Interfaces for callbacks
    interface OnZoomListener {
        fun onZoomStarted()
        fun onZoomChanged(scale: Float)
        fun onZoomEnded()
        fun onDoubleTap()
        fun onSingleTap()
    }

    private var zoomListener: OnZoomListener? = null

    init {
        // Initialize matrix
        imageMatrix = matrix
//        scaleType = ScaleType.MATRIX
        
        // Set up gesture detectors
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    fun setOnZoomListener(listener: OnZoomListener) {
        zoomListener = listener
    }

    fun resetZoom() {
        matrix.reset()
        currentScale = 1f
        isZoomed = false
        imageMatrix = matrix
        invalidate()
    }

    fun getCurrentScale(): Float {
        return currentScale
    }

    fun isZoomed(): Boolean {
        return isZoomed
    }

    override fun setImageMatrix(matrix: Matrix?) {
        super.setImageMatrix(matrix)
        if (matrix != null) {
            this.matrix.set(matrix)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let scale gesture detector handle the event first
        scaleGestureDetector.onTouchEvent(event)
        
        // Let gesture detector handle the event
        gestureDetector.onTouchEvent(event)
        
        // Handle multi-touch for panning when zoomed
        if (event.pointerCount >= 2) {
            handleMultiTouch(event)
        }
        
        return true
    }

    private fun handleMultiTouch(event: MotionEvent) {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startPoint.set(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint.set(getMidPoint(event))
                }
                lastEvent = floatArrayOf(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        matrix.set(savedMatrix)
                        val scale = newDist / oldDist
                        matrix.postScale(scale, scale, midPoint.x, midPoint.y)
                        fixScaleAndTranslation()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                zoomListener?.onZoomEnded()
            }
        }
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun getMidPoint(event: MotionEvent): PointF {
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        return PointF(x, y)
    }

    private fun fixScaleAndTranslation() {
        // Get the current scale
        val values = FloatArray(9)
        matrix.getValues(values)
        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        
        currentScale = scaleX
        
        // Check if we've zoomed in or out
        if (currentScale > 1f) {
            isZoomed = true
        } else if (currentScale <= 1f) {
            isZoomed = false
        }
        
        zoomListener?.onZoomChanged(currentScale)
        
        // Limit scale
        if (scaleX < minScale) {
            matrix.postScale(minScale / scaleX, minScale / scaleY, midPoint.x, midPoint.y)
        } else if (scaleX > maxScale) {
            matrix.postScale(maxScale / scaleX, maxScale / scaleY, midPoint.x, midPoint.y)
        }
        
        // Fix translation when zoomed
        if (isZoomed) {
            fixTranslation()
        } else {
            // Reset to center when not zoomed
            matrix.postTranslate(
                (width / 2 - values[Matrix.MTRANS_X]).toFloat(),
                (height / 2 - values[Matrix.MTRANS_Y]).toFloat()
            )
        }
        
        imageMatrix = matrix
    }

    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scaleX = values[Matrix.MSCALE_X]
        
        val drawableWidth = (drawable?.intrinsicWidth ?: width).toFloat()
        val drawableHeight = (drawable?.intrinsicHeight ?: height).toFloat()
        
        val actWidth = drawableWidth * scaleX
        val actHeight = drawableHeight * scaleX
        
        // Calculate bounds
        val left = width - actWidth
        val right = 0f
        val top = height - actHeight
        val bottom = 0f
        
        // Clamp translation
        var newTransX = transX
        var newTransY = transY
        
        if (transX < left) {
            newTransX = left
        } else if (transX > right) {
            newTransX = right
        }
        
        if (transY < top) {
            newTransY = top
        } else if (transY > bottom) {
            newTransY = bottom
        }
        
        if (newTransX != transX || newTransY != transY) {
            matrix.postTranslate(newTransX - transX, newTransY - transY)
            imageMatrix = matrix
        }
    }

    // Scale gesture listener for pinch-to-zoom
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomListener?.onZoomStarted()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            matrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
            fixScaleAndTranslation()
            
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            zoomListener?.onZoomEnded()
        }
    }

    // Gesture listener for double tap and single tap
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomListener?.onDoubleTap()
            handleDoubleTap(e)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            zoomListener?.onSingleTap()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }

    private fun handleDoubleTap(event: MotionEvent) {
        if (isZoomed) {
            // Reset to original scale
            resetZoom()
        } else {
            // Zoom in to max scale at the tap point
            val scale = maxScale
            matrix.postScale(scale, scale, event.x, event.y)
            fixScaleAndTranslation()
            isZoomed = true
        }
    }

    fun setMaxScale(max: Float) {
        maxScale = max
    }

    fun setMinScale(min: Float) {
        minScale = min
    }
}