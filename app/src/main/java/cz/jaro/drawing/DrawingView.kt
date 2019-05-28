package cz.jaro.drawing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View


class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val tag = DrawingView::class.java.name

    private var bitmap: Bitmap
    private val canvas: Canvas = Canvas()
    private var hasPersistedCurve: Boolean = false

    private val curves: MutableMap<Int, MyCurve> = HashMap() // Key is pointerId
    private val nonPersistedCurves: MutableSet<MyCurve> = HashSet()

    private var barsAppearedTime: Long = 0 // Time in ms at which the notification bar (and navigation bar) appeared the last time

    private var lastColor = Color.TRANSPARENT // The color of the last completed curve

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

        // Switch non-persisted curves to persisted if they are old enough
        val now = System.currentTimeMillis()
        val toRemove: MutableSet<MyCurve> = HashSet()
        for (curve in nonPersistedCurves)
            if (TIME_AROUND_BARS < now - curve.createTime) { // If the curve is older than one second
                (getActivity() as DrawingActivity).log(Log.DEBUG, "Persisting curve ${curve.createTime % 1000}")
                toRemove.add(curve)
                curve.draw(canvas)
                hasPersistedCurve = true
            }
        nonPersistedCurves.removeAll(toRemove)

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                // The color of last completed curve is forbidden.
                val forbiddenColors = HashSet<Int>()
                forbiddenColors.add(lastColor)
                for (curve in curves.values) {
                    forbiddenColors.add(curve.color())
                }
                val curve = MyCurve(context, forbiddenColors)

                val point = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                curve.addPoint(point)

                curves[pointerId] = curve

                (getActivity() as DrawingActivity).log(Log.DEBUG, "Starting curve ${curve.createTime % 1000}")
                nonPersistedCurves.add(curve)

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

                        val isPersisted = !nonPersistedCurves.contains(curve)
                        if (isPersisted)
                            curve.drawLastSegment(canvas)
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
                    curves.remove(pointerId)

                    lastColor = curve.color()

                    val isPersisted = !nonPersistedCurves.contains(curve)
                    if (!isPersisted) // If the curve is non-persistent ...
                        if (now - barsAppearedTime < TIME_AROUND_BARS) { // ... and status bar appeared in the last second
                            (getActivity() as DrawingActivity).log(Log.DEBUG, "Ending and cancelling non-persistent curve ${curve.createTime % 1000}")
                            nonPersistedCurves.remove(curve)
                            invalidate()
                        } else {
                            (getActivity() as DrawingActivity).log(Log.DEBUG, "Ending and keeping non-persistent curve ${curve.createTime % 1000}")
                            curve.endTime = now
                        }
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw image (with finished curved)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Draw non-persisted curves (in the order as they were drawn)
        nonPersistedCurves.toList().sortedBy { it.createTime }.forEach { it.draw(canvas) }
    }

    fun onBarsAppeared() {
        (getActivity() as DrawingActivity).log(Log.VERBOSE, "onBarsAppeared()")

        barsAppearedTime = System.currentTimeMillis()
    }

    fun clear() {
        // Clear the open curves
        curves.clear()
        nonPersistedCurves.clear()

        // Clear the canvas
        val whitePaint = Paint()
        whitePaint.color = Color.WHITE
        whitePaint.style = Paint.Style.FILL
        canvas.drawPaint(whitePaint)

        hasPersistedCurve = false

        invalidate()
    }

    fun isEmpty(): Boolean {
        return !hasPersistedCurve && nonPersistedCurves.isEmpty()
    }

    @SuppressLint("WrongCall")
    fun getDrawingBitmap(): Bitmap {
        val out = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(out)

        onDraw(canvas)

        return out
    }

    private fun getActivity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
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

    companion object {
        private const val TIME_AROUND_BARS = 1000 // in ms
    }
}