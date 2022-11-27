package app.familygem

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import android.view.VelocityTracker
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.MotionEvent
import android.view.View
import graph.gedcom.Graph

class MoveLayout(context: Context?, attributeSet: AttributeSet?) : FrameLayout(
    context!!, attributeSet
) {
    var graph: Graph? = null
    private var child: View? = null
    val scaleDetector: ScaleGestureDetector
    private val scroller: OverScroller
    private var velocityTracker: VelocityTracker? = null
    var mWidth = 0
    var mHeight = 0
    var childWidth = 0
    var childHeight = 0
    private var lastX = 0
    private var lastY = 0
    private var downX = 0f
    private var downY = 0f
    private var overX = 0
    private var overY = 0
    private var mendX = 0
    private var mendY // Position correction for the child with scaled size
            = 0
    var scale = .7f
    var scaling // the screen has been touched with two fingers
            = false
    var leftToRight // LTR (otherwise RTL)
            = false

    init {
        val scaleListener: SimpleOnScaleGestureListener = object : SimpleOnScaleGestureListener() {
            override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
                val scaleFactor = scaleGestureDetector.scaleFactor
                val minimum = Math.min(mWidth.toFloat() / childWidth, mHeight.toFloat() / childHeight)
                scale = Math.max(minimum, scale * scaleFactor)
                if (scale > 5) {
                    scale = child!!.scaleX
                    return false
                }
                child!!.scaleX = scale
                child!!.scaleY = scale
                calcOverScroll(true)
                // Corrects scroll while scaling
                var distX: Float
                distX =
                    if (leftToRight) childWidth / 2f - scrollX - scaleGestureDetector.focusX else mWidth - (scrollX + childWidth / 2f) - scaleGestureDetector.focusX
                distX -= distX * scaleFactor
                var distY = childHeight / 2f - scrollY - scaleGestureDetector.focusY
                distY -= distY * scaleFactor
                scrollBy(distX.toInt(), distY.toInt())
                lastX = scrollX
                lastY = scrollY
                return true
            }
        }
        scaleDetector = ScaleGestureDetector(context!!, scaleListener)
        scroller = OverScroller(context)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mWidth = MeasureSpec.getSize(widthMeasureSpec)
        mHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(mWidth, mHeight)
        child = getChildAt(0)
        child!!.scaleX = scale
        child!!.scaleY = scale

        // Measure the child as unspecified
        val spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measureChildren(spec, spec)
    }

    /**
     * Intercept motion events also on children with click listener (person cards)
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                postInvalidateOnAnimation()
                calcOverScroll(true)
                lastX = scrollX
                lastY = scrollY
                downX = event.x
                downY = event.y
                if (velocityTracker == null) velocityTracker =
                    VelocityTracker.obtain() else velocityTracker!!.clear()
                velocityTracker!!.addMovement(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> scaling = true
            MotionEvent.ACTION_MOVE -> if (Math.abs(downX - event.x) > 10 || Math.abs(downY - event.y) > 10) {
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_POINTER_DOWN -> {
                scaling = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var scrollX = (lastX + downX - event.x).toInt()
                var scrollY = (lastY + downY - event.y).toInt()
                // Horizontal limits
                if (leftToRight) {
                    if (scrollX < mendX - overX) // Left
                        scrollX =
                            mendX - overX else if (scrollX > childWidth - mWidth + overX - mendX) // Right
                        scrollX = childWidth - mWidth + overX - mendX
                } else { // RTL
                    if (scrollX > overX - mendX) // Right
                        scrollX =
                            overX - mendX else if (scrollX < mWidth - childWidth - overX + mendX) // Left
                        scrollX = mWidth - childWidth - overX + mendX
                }
                // Vertical limits
                if (scrollY < mendY - overY) // Top
                    scrollY =
                        mendY - overY else if (scrollY > childHeight - mHeight + overY - mendY) // Bottom
                    scrollY = childHeight - mHeight + overY - mendY
                if (!scaling) {
                    scrollTo(scrollX, scrollY)
                }
                velocityTracker!!.addMovement(event)
                velocityTracker!!.computeCurrentVelocity(1000)
                return true
            }
            MotionEvent.ACTION_UP -> {
                scaling = false
                if (leftToRight) {
                    scroller.fling(
                        scrollX,
                        scrollY,
                        -velocityTracker!!.xVelocity.toInt(),
                        -velocityTracker!!.yVelocity.toInt(),
                        mendX,
                        childWidth - mWidth - mendX,
                        mendY,
                        childHeight - mHeight - mendY,
                        overX,
                        overY
                    )
                } else {
                    scroller.fling(
                        scrollX,
                        scrollY,
                        -velocityTracker!!.xVelocity.toInt(),
                        -velocityTracker!!.yVelocity.toInt(),
                        mWidth - childWidth + mendX,
                        -mendX,
                        mendY,
                        childHeight - mHeight - mendY,
                        overX,
                        overY
                    )
                }
                postInvalidate() //invalidate(); superfluous?
                //velocityTracker.recycle(); // throws IllegalStateException: Already in the pool!
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            invalidate()
        }
    }

    /**
     * Calculate overscroll and mend
     * @param centering Add to 'mendX' and to 'mendY' the space to center a small child inside moveLayout
     */
    fun calcOverScroll(centering: Boolean) {
        overX = (mWidth / 4 * scale).toInt()
        overY = (mHeight / 4 * scale).toInt()
        mendX = (childWidth - childWidth * scale).toInt() / 2
        if (centering && childWidth * scale < mWidth) mendX -= ((mWidth - childWidth * scale) / 2).toInt()
        mendY = (childHeight - childHeight * scale).toInt() / 2
        if (centering && childHeight * scale < mHeight) mendY -= ((mHeight - childHeight * scale) / 2).toInt()
    }

    fun minimumScale(): Float {
        if (childWidth * scale < mWidth && childHeight * scale < mHeight) {
            scale = Math.min(mWidth.toFloat() / childWidth, mHeight.toFloat() / childHeight)
            child!!.scaleX = scale
            child!!.scaleY = scale
        }
        return scale
    }

    /**
     * Scroll to x and y
     */
    fun panTo(x: Int, y: Int) {
        var x = x
        var y = y
        calcOverScroll(false)
        // Remove excessive space around
        if (childHeight * scale - y < mHeight) // There is space below
            y = (childHeight * scale - mHeight).toInt()
        if (y < 0) // There is space above
            y = Math.min(0, (childHeight * scale - mHeight).toInt() / 2)
        if (leftToRight) {
            if (childWidth * scale - x < mWidth) // There is space on the right
                x = (childWidth * scale - mWidth).toInt()
            if (x < 0) // There is space on the left
                x = Math.min(0, (childWidth * scale - mWidth).toInt() / 2)
            scrollTo(x + mendX, y + mendY)
        } else { // RTL
            if (childWidth * scale + x < mWidth) // There is space on the left
                x = -(childWidth * scale - mWidth).toInt()
            if (x > 0) // There is space on the right
                x = Math.max(0, -(childWidth * scale - mWidth).toInt() / 2)
            scrollTo(x - mendX, y + mendY)
        }
    }

    fun displayAll() {
        scale = 0f
        minimumScale()
        calcOverScroll(true)
        scrollTo(if (leftToRight) mendX else -mendX, mendY)
    }

    override fun onDraw(canvas: Canvas) {
        // Pass the max possible size of a bitmap to the graph, so it can distribute lines in groups
        if (graph!!.needMaxBitmapSize()) {
            graph!!.maxBitmapSize =
                (U.pxToDp(canvas.maximumBitmapWidth.toFloat()) // 4096 on my old physical devices, 16384 on the new ones
                        - 10) // The space actually occupied by a path is a little bit larger
        }
    }
}