package fr.vpm.yag.ui.album

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView supporting pinch-to-zoom, panning and double-tap zoom.
 *
 * The image is displayed "fit center" through [baseMatrix]; the user's zoom and pan are applied on
 * top of it through [zoomMatrix]. While zoomed, the parent (ViewPager2) is prevented from stealing
 * the gesture, unless the photo is panned against its edge so the user can still swipe to the next one.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // Fits the drawable in the view, like ScaleType.FIT_CENTER
    private val baseMatrix = Matrix()
    // User zoom and pan, applied on top of the base matrix
    private val zoomMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val displayRect = RectF()
    private val matrixValues = FloatArray(9)

    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private var maxScale = 4f
    private var minScale = 1f
    private var doubleTapScale = 2.5f

    interface OnZoomListener {
        fun onZoomStarted()
        fun onZoomChanged(scale: Float)
        fun onZoomEnded()
        fun onDoubleTap()
        fun onSingleTap()
    }

    private var zoomListener: OnZoomListener? = null

    init {
        super.setScaleType(ScaleType.MATRIX)
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    fun setOnZoomListener(listener: OnZoomListener) {
        zoomListener = listener
    }

    fun resetZoom() {
        zoomMatrix.reset()
        applyMatrix()
    }

    fun getCurrentScale(): Float {
        zoomMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    fun isZoomed(): Boolean = getCurrentScale() > 1.01f

    fun setMaxScale(max: Float) {
        maxScale = max
    }

    fun setMinScale(min: Float) {
        minScale = min
    }

    // The scale type is always MATRIX: fitting is handled by baseMatrix
    override fun setScaleType(scaleType: ScaleType?) {}

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        zoomMatrix.reset()
        updateBaseMatrix()
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        if (changed) {
            updateBaseMatrix()
        }
        return changed
    }

    private fun updateBaseMatrix() {
        val d = drawable
        baseMatrix.reset()
        if (d != null && width > 0 && height > 0 && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
            baseMatrix.setRectToRect(
                RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat()),
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Matrix.ScaleToFit.CENTER
            )
        }
        fixTranslation()
        applyMatrix()
    }

    private fun applyMatrix() {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(zoomMatrix)
        imageMatrix = drawMatrix
    }

    private fun currentDisplayRect(): RectF? {
        val d = drawable ?: return null
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(zoomMatrix)
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(displayRect)
        return displayRect
    }

    // Keeps the image covering the view when it is larger than it, and centred otherwise
    private fun fixTranslation() {
        val rect = currentDisplayRect() ?: return
        val dx = when {
            rect.width() <= width -> (width - rect.width()) / 2f - rect.left
            rect.left > 0 -> -rect.left
            rect.right < width -> width - rect.right
            else -> 0f
        }
        val dy = when {
            rect.height() <= height -> (height - rect.height()) / 2f - rect.top
            rect.top > 0 -> -rect.top
            rect.bottom < height -> height - rect.bottom
            else -> 0f
        }
        zoomMatrix.postTranslate(dx, dy)
    }

    private fun zoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        val factor = targetScale.coerceIn(minScale, maxScale) / getCurrentScale()
        zoomMatrix.postScale(factor, factor, focusX, focusY)
        fixTranslation()
        applyMatrix()
        zoomListener?.onZoomChanged(getCurrentScale())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            // Panning a zoomed photo, or pinching, must not be taken over by the parent as a page swipe
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(isZoomed())
            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomListener?.onZoomStarted()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(getCurrentScale() * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            zoomListener?.onZoomEnded()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!isZoomed() || scaleGestureDetector.isInProgress) return false

            zoomMatrix.postTranslate(-distanceX, -distanceY)
            fixTranslation()
            applyMatrix()

            // Once panned against the edge, let the parent swipe to the neighbouring photo
            val rect = currentDisplayRect() ?: return true
            val atLeftEdge = rect.left >= -1f && distanceX < 0
            val atRightEdge = rect.right <= width + 1f && distanceX > 0
            if (e2.pointerCount == 1 && (atLeftEdge || atRightEdge)) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomListener?.onDoubleTap()
            if (isZoomed()) {
                resetZoom()
                zoomListener?.onZoomChanged(getCurrentScale())
            } else {
                zoomTo(doubleTapScale, e.x, e.y)
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            zoomListener?.onSingleTap()
            return true
        }
    }
}
