package fr.vpm.yag.ui.album

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * A simple flow layout that arranges child views horizontally, wrapping to the next line
 * when there's not enough space.
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private var horizontalSpacing = 8
    private var verticalSpacing = 4

    init {
        val density = resources.displayMetrics.density
        horizontalSpacing = (8 * density).toInt()
        verticalSpacing = (2 * density).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0
        var currentLineWidth = 0
        var currentLineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            // Check if we need to wrap to the next line
            if (currentLineWidth + childWidth > widthSize) {
                // Move to next line
                width = Math.max(width, currentLineWidth)
                height += currentLineHeight + verticalSpacing
                currentLineWidth = childWidth
                currentLineHeight = childHeight
            } else {
                // Add to current line
                currentLineWidth += childWidth + horizontalSpacing
                currentLineHeight = Math.max(currentLineHeight, childHeight)
            }
        }

        // Add the last line
        width = Math.max(width, currentLineWidth)
        height += currentLineHeight

        // Account for padding
        width += paddingLeft + paddingRight
        height += paddingTop + paddingBottom

        setMeasuredDimension(
            if (widthMode == MeasureSpec.EXACTLY) widthSize else width,
            if (heightMode == MeasureSpec.EXACTLY) heightSize else height
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var currentX = paddingLeft
        var currentY = paddingTop
        var currentLineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            // Check if we need to wrap to the next line
            if (currentX + childWidth > r - paddingRight) {
                // Move to next line
                currentX = paddingLeft
                currentY += currentLineHeight + verticalSpacing
                currentLineHeight = childHeight
            }

            // Layout the child
            child.layout(currentX, currentY, currentX + childWidth, currentY + childHeight)
            currentX += childWidth + horizontalSpacing
            currentLineHeight = Math.max(currentLineHeight, childHeight)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p != null
    }
}

