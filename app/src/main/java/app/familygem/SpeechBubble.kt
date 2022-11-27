package app.familygem

import android.app.Activity
import android.content.Context
import android.os.Handler
import app.familygem.R
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View.OnTouchListener
import android.view.MotionEvent
import android.os.Looper
import android.view.View

/**
 * Speech bubble with a hint appearing above the FAB
 */
class SpeechBubble(context: Context, testo: String?) {
    private val bubble: View

    constructor(context: Context, textId: Int) : this(context, context.getString(textId)) {}

    init {
        val activity = context as Activity
        bubble = activity.layoutInflater.inflate(R.layout.fabuloso, null)
        bubble.visibility = View.INVISIBLE
        (activity.findViewById<View>(R.id.fab_box) as LinearLayout).addView(
            bubble, 0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        (bubble.findViewById<View>(R.id.fabuloso_text) as TextView).text = testo
        bubble.setOnTouchListener { view: View?, event: MotionEvent? ->
            hide()
            true
        }
        activity.findViewById<View>(R.id.fab)
            .setOnTouchListener { view: View?, event: MotionEvent? ->
                hide()
                false // To execute click later
            }
    }

    fun show() {
        Handler(Looper.myLooper()!!).postDelayed({ // appears after one second
            bubble.visibility = View.VISIBLE
        }, 1000)
    }

    fun hide() {
        bubble.visibility = View.INVISIBLE
    }
}