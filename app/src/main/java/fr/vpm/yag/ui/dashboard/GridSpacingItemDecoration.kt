package fr.vpm.yag.ui.dashboard

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val context: Context,
    private val spanCount: Int,
    spacingDp: Int = 1
) : RecyclerView.ItemDecoration() {
    
    private val spacing: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        spacingDp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount
        
        // Add spacing to left of all items except first column
        outRect.left = if (column > 0) spacing else 0
        // Add spacing to right of all items except last column
        outRect.right = if (column < spanCount - 1) spacing else 0
        // Add spacing to top of all items except first row
        outRect.top = if (position >= spanCount) spacing else 0
        // Add spacing to bottom of all items
        outRect.bottom = spacing
    }
}
