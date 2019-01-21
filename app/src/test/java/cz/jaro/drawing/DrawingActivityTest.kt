package cz.jaro.drawing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class DrawingActivityTest {
    @Test
    fun gesture() {
        val drawingActivity = DrawingActivity()

        for (i in 0..5) {
            drawingActivity.sensorRecords.add(OrientationRecord(0, floatArrayOf((Math.PI / 10 * i).toFloat(), 0f, 0f)))
            assertFalse("Turning away i=${i}", drawingActivity.gesturePerformed())
        }
        for (i in 6 downTo 3) {
            drawingActivity.sensorRecords.add(OrientationRecord(0, floatArrayOf((Math.PI / 10 * i).toFloat(), 0f, 0f)))
            assertFalse("Turning near i=${i}", drawingActivity.gesturePerformed())
        }

        val i = 2
        drawingActivity.sensorRecords.add(OrientationRecord(0, floatArrayOf((Math.PI / 10 * i).toFloat(), 0f, 0f)))
        assertTrue(drawingActivity.gesturePerformed())
    }
}
