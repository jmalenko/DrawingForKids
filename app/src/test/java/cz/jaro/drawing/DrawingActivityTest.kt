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
    val drawingActivity = DrawingActivity()

    val epsilon = Math.toRadians(2.1)

    @Test
    fun gesture() {
        for (deg in 0..150 step 10) {
            drawingActivity.sensorRecords.add(OrientationRecord(0, vectorDegToRad(deg.toFloat(), 0f, 0f)))
            assertFalse("Turning away $deg deg", drawingActivity.gesturePerformed())
        }
        for (deg in 140 downTo 60 step 10) {
            drawingActivity.sensorRecords.add(OrientationRecord(0, vectorDegToRad(deg.toFloat(), 0f, 0f)))
            assertFalse("Turning back $deg deg", drawingActivity.gesturePerformed())
        }

        val deg = 50
        drawingActivity.sensorRecords.add(OrientationRecord(0, vectorDegToRad(deg.toFloat(), 0f, 0f)))
        assertTrue("Gesture completed", drawingActivity.gesturePerformed())
    }

    @Test
    fun angle() {
        var o1: FloatArray
        var o2: FloatArray
        var angle: Double
        var p: Float
        var angleExpected: Double

        // 1st element overrun
        o1 = vectorDegToRad(+179f, 0f, 0f)
        o2 = vectorDegToRad(-179f, 0f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        o1 = vectorDegToRad(-179f, 0f, 0f)
        o2 = vectorDegToRad(+179f, 0f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        // 2nd element overrun
        o1 = vectorDegToRad(0f, +89f, 0f)
        o2 = vectorDegToRad(0f, -89f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        o1 = vectorDegToRad(0f, -89f, 0f)
        o2 = vectorDegToRad(0f, +89f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        // 3rd element overrun
        o1 = vectorDegToRad(0f, 0f, +179f)
        o2 = vectorDegToRad(0f, 0f, -179f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        o1 = vectorDegToRad(0f, 0f, -179f)
        o2 = vectorDegToRad(0f, 0f, +179f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be almost zero", angle < epsilon)
        assertTrue("Angle ($angle) should be non-negative", 0 <= angle)

        // 1st element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, 0f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $p", angleExpected - epsilon < angle)
        assertTrue("Angle ($angle) should be around $p", angle < angleExpected + epsilon)

        // 2nd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, p, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $p", angleExpected - epsilon < angle)
        assertTrue("Angle ($angle) should be around $p", angle < angleExpected + epsilon)

        // 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, 0f, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $p", angleExpected - epsilon < angle)
        assertTrue("Angle ($angle) should be around $p", angle < angleExpected + epsilon)

        // 1st & 2nd element distance
        p = 60f
        angleExpected = Math.toRadians(pyth(p))
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, p, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(pyth(p))
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, 0f, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 2nd & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(pyth(p))
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, p, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st & 2nd & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(pyth3(p))
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, p, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st element all distances
        for (i in 10..180 step 10) {
            p = i.toFloat()
            angleExpected = Math.toRadians(p.toDouble())
            o1 = vectorDegToRad(0f, 0f, 0f)
            o2 = vectorDegToRad(p, 0f, 0f)
            angle = drawingActivity.angleBetweenOrientations(o1, o2)
            assertTrue("Angle ($angle) should be around $p", angleExpected - epsilon < angle)
            assertTrue("Angle ($angle) should be around $p", angle < angleExpected + epsilon)
        }
    }

    private fun vectorDegToRad(x: Float, y: Float, z: Float): FloatArray {
        return floatArrayOf(
                Math.toRadians(x.toDouble()).toFloat(),
                Math.toRadians(y.toDouble()).toFloat(),
                Math.toRadians(z.toDouble()).toFloat()
        )

    }

    fun pyth(a: Float): Double {
        return Math.sqrt((2 * a * a).toDouble())
    }

    fun pyth3(a: Float): Double {
        return Math.sqrt((3 * a * a).toDouble())
    }
}
