package cz.jaro.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View


class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val tag = DrawingView::class.java.name

    internal var bitmap: Bitmap
    private val canvas: Canvas = Canvas()

    private val curves: MutableMap<Int, MyCurve> = HashMap() // Key is pointerId

    init {
        bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888) // Use a constant size. This will be resized in onSizeChanged(...) which will be called before the activity appears
        canvas.setBitmap(bitmap)
        clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        bitmap = Bitmap.createScaledBitmap(bitmap, w, h, false)
        canvas.setBitmap(bitmap)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        Log.d(tag, "onTouchEvent() action=${actionToString(action)} ($action), pointerCount=${event.pointerCount}, actionIndex=${event.actionIndex}")

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                val curve = MyCurve(context)

                val point = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                curve.addPoint(point)

                curves[pointerId] = curve

                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    Log.v(tag, "pointerIndex=$pointerIndex, pointerId=$pointerId, x=${Math.round(event.getX(pointerIndex))}, y=${Math.round(event.getY(pointerIndex))}")
                    val point = PointF(event.getX(pointerIndex), event.getY(pointerIndex))

                    val curve = curves[pointerId]
                    if (curve != null) {
                        curve.addPoint(point)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                val curve = curves[pointerId]
                if (curve != null) {
                    curve.draw(canvas)

                    curves.remove(pointerId)

                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw image (with finished curved)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Draw open curves
        for (curve: MyCurve in curves.values)
            curve.draw(canvas)
    }

    fun clear() {
        // Clear the open curves
        curves.clear()

        // Clear the canvas
        val whitePaint = Paint()
        whitePaint.color = Color.WHITE
        whitePaint.style = Paint.Style.FILL
        canvas.drawPaint(whitePaint)

        invalidate()
    }

    /**
     * Given an action int, returns a string description
     *
     * @param action Action id
     * @return String description of the action
     */
    private fun actionToString(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "Down"
            MotionEvent.ACTION_POINTER_DOWN -> "Pointer Down"
            MotionEvent.ACTION_MOVE -> "Move"
            MotionEvent.ACTION_UP -> "Up"
            MotionEvent.ACTION_POINTER_UP -> "Pointer Up"
            MotionEvent.ACTION_OUTSIDE -> "Outside"
            MotionEvent.ACTION_CANCEL -> "Cancel"
            else -> "?"
        }
    }
}