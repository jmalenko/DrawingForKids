package cz.jaro.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import java.util.*
import kotlin.collections.ArrayList

class MyCurve(context: Context, forbiddenColor: Int) {

    private val points: MutableList<PointF> = ArrayList()
    private val paint = Paint()
    private val width: Float

    val createTime: Long = System.currentTimeMillis()
    var endTime: Long = 0

    init {
        // Set stroke width
        width = mmToPx(STROKE_WIDTH_MM, context)

        // Set painting properties
        paint.isAntiAlias = true
        paint.isDither = true
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND

        // Set stroke color
        // Pick a random color and check that it is not fordidden.
        val random = Random()
        var randomColorId = colors[random.nextInt(MyCurve.colors.size)]
        var color = ContextCompat.getColor(context, randomColorId)
        while (color == forbiddenColor) {
            randomColorId = colors[random.nextInt(MyCurve.colors.size)]
            color = ContextCompat.getColor(context, randomColorId)
        }
        paint.color = color
    }

    private fun mmToPx(mm: Float, context: Context): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm, context.resources.displayMetrics)
    }

    fun addPoint(point: PointF) {
        points.add(point)
    }

    /**
     * Draws the curve to the canvas.
     */
    fun draw(canvas: Canvas) {
        for (i in 0 until points.size) {
            drawSegment(canvas, i)
        }
    }

    /**
     * Draws the curve last segment to the canvas.
     */
    fun drawLastSegment(canvas: Canvas) {
        drawSegment(canvas, points.size - 1)
    }

    private fun drawSegment(canvas: Canvas, index: Int) {
        when (index) {
            0 -> {
                val point = points[index]
                canvas.drawCircle(point.x, point.y, width / 2, paint)
            }
            else -> {
                val from = points[index]
                val to = points[index - 1]
                canvas.drawLine(from.x, from.y, to.x, to.y, paint)
            }
        }
    }

    fun color(): Int {
        return paint.color
    }

    companion object {
        // 6 mm is ok for kids
        // 12 mm is just slightly bigger than an adult fingertip
        const val STROKE_WIDTH_MM = 5f

        val colors = arrayOf(
                // Grays
                R.color.S_1000_N, R.color.S_3000_N, R.color.S_5000_N, R.color.S_7000_N, R.color.S_9000_N,
                // Basic colors
                R.color.S_1080_R, R.color.S_1040_R,
                R.color.S_4055_B, R.color.S_1040_B,
                R.color.S_1080_G30Y, R.color.S_1040_G30Y,
                R.color.S_0570_Y, R.color.S_0540_Y,
                // Combinations
                R.color.S_3055_R50B, R.color.S_1040_R50B,
                R.color.S_3055_B50G, R.color.S_1040_B50G,
                R.color.S_1075_G50Y, R.color.S_1040_G50Y,
                R.color.S_1080_Y50R, R.color.S_1040_Y50R,
                // Other
                R.color.S_5040_Y30R
        )
    }
}
