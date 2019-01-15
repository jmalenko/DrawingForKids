package cz.jaro.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import java.util.*
import kotlin.collections.ArrayList

class MyCurve(context: Context) {

    private val points: MutableList<PointF> = ArrayList()
    private val paint = Paint()
    private val width: Float

    init {
        // Set stroke width
        width = mmToPx(STROKE_WIDTH_MM, context)

        // Set painting properties
        paint.isAntiAlias = true
        paint.isDither = true
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND

        // Set stroke color
        val random = Random()
        val randomColorId = MyCurve.colors[random.nextInt(MyCurve.colors.size)]
        paint.color = ContextCompat.getColor(context, randomColorId)
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
        if (0 < points.size) {
            val point = points[0]
            canvas.drawCircle(point.x, point.y, width / 2, paint)
        }

        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }

    companion object {
        const val STROKE_WIDTH_MM = 12f // 12 mm is just slightly bigger than the adults fingertip

        val colors = arrayOf(
                // Grays
                R.color.S_1000_N, R.color.S_3000_N, R.color.S_5000_N, R.color.S_7000_N, R.color.S_9000_N,
                // Basic colors, 4 saturation levels
                R.color.S_1020_B, R.color.S_1020_G, R.color.S_1020_R, R.color.S_1020_Y,
                R.color.S_1040_B, R.color.S_1040_G, R.color.S_1040_R, R.color.S_1040_Y,
                R.color.S_1060_B, R.color.S_1060_G, R.color.S_1060_R, R.color.S_1060_Y,
                // Basic colors - extremes
                R.color.S_1080_R, R.color.S_1080_Y,
                R.color.S_1080_G30Y,
                // Combined colors, 3 saturation levels
                R.color.S_1020_B50G, R.color.S_1020_G50Y, R.color.S_1020_Y50R, R.color.S_1020_R50B,
                R.color.S_1040_B50G, R.color.S_1040_G50Y, R.color.S_1040_Y50R, R.color.S_1040_R50B,
                R.color.S_1060_G50Y, R.color.S_1060_Y50R
        )

    }
}