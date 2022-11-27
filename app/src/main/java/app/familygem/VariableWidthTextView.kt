package app.familygem

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import android.view.View.MeasureSpec

/**
 * TextView that adapts the width even to multiple lines
 * TextView che adatta la larghezza anche a molteplici linee
 */
class VariableWidthTextView(context: Context?, attrs: AttributeSet?) : AppCompatTextView(
    context!!, attrs
) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var widthSpec = widthSpec
        val widthMode = MeasureSpec.getMode(widthSpec)
        if (widthMode == MeasureSpec.AT_MOST) {
            val layout = layout
            if (layout != null) {
                val maxWidth = Math.ceil(getMaxLineWidth(layout).toDouble()).toInt() +
                        compoundPaddingLeft + compoundPaddingRight
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    private fun getMaxLineWidth(layout: Layout): Float {
        var max_width = 0.0f
        val lines = layout.lineCount
        for (i in 0 until lines) {
            if (layout.getLineWidth(i) > max_width) {
                max_width = layout.getLineWidth(i)
            }
        }
        return max_width
    }
}