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

    val epsilon = Math.toRadians(0.1)

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
        o1 = vectorDegToRad(1f, 0f, 0f)
        o2 = vectorDegToRad(359f, 0f, 0f)
        angleExpected = Math.toRadians(2.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)


        o1 = vectorDegToRad(359f, 0f, 0f)
        o2 = vectorDegToRad(1f, 0f, 0f)
        angleExpected = Math.toRadians(2.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 2nd element overrun
        o1 = vectorDegToRad(0f, +89f, 0f)
        o2 = vectorDegToRad(0f, -89f, 0f)
        angleExpected = Math.toRadians(178.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        o1 = vectorDegToRad(0f, -89f, 0f)
        o2 = vectorDegToRad(0f, +89f, 0f)
        angleExpected = Math.toRadians(178.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 3rd element overrun
        o1 = vectorDegToRad(0f, 0f, +89f)
        o2 = vectorDegToRad(0f, 0f, -89f)
        angleExpected = Math.toRadians(178.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        o1 = vectorDegToRad(0f, 0f, -89f)
        o2 = vectorDegToRad(0f, 0f, +89f)
        angleExpected = Math.toRadians(178.0)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, 0f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 2nd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, p, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, 0f, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st element, all distances from zero
        for (deg in 10..360 step 10) {
            p = deg.toFloat()
            angleExpected = Math.toRadians((if (deg < 180) deg else 360 - deg).toDouble())
            o1 = vectorDegToRad(0f, 0f, 0f)
            o2 = vectorDegToRad(p, 0f, 0f)
            angle = drawingActivity.angleBetweenOrientations(o1, o2)
            assertTrue("For deg=$deg, angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)
        }

        // 2nd element, all distances from zero
        for (deg in -90..90 step 10) {
            p = deg.toFloat()
            angleExpected = Math.toRadians(Math.abs(p.toDouble()))
            o1 = vectorDegToRad(0f, 0f, 0f)
            o2 = vectorDegToRad(0f, p, 0f)
            angle = drawingActivity.angleBetweenOrientations(o1, o2)
            assertTrue("For deg=$deg, angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)
        }

        // 3rd element, all distances from zero
        for (deg in -90..90 step 10) {
            p = deg.toFloat()
            angleExpected = Math.toRadians(Math.abs(p.toDouble()))
            o1 = vectorDegToRad(0f, 0f, 0f)
            o2 = vectorDegToRad(0f, p, 0f)
            angle = drawingActivity.angleBetweenOrientations(o1, o2)
            assertTrue("For deg=$deg, angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)
        }

        // 1st & 2nd element distance
        p = 60f
        angleExpected = Math.toRadians(75.52)
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, p, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, 0f, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 2nd & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(p.toDouble())
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(0f, p, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle ($angle) should be around $angleExpected ($p deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st & 2nd & 3rd element distance
        p = 60f
        angleExpected = Math.toRadians(75.52)
        o1 = vectorDegToRad(0f, 0f, 0f)
        o2 = vectorDegToRad(p, p, p)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)

        // 1st & 2nd element distance
        angleExpected = Math.toRadians(2.0)
        o1 = vectorDegToRad(0f, 89f, 0f)
        o2 = vectorDegToRad(180f, 89f, 0f)
        angle = drawingActivity.angleBetweenOrientations(o1, o2)
        assertTrue("Angle $angle (${Math.toDegrees(angle)} deg) should be around $angleExpected (${Math.toDegrees(angleExpected)} deg)", angle in angleExpected - epsilon..angleExpected + epsilon)
    }

    private fun vectorDegToRad(x: Float, y: Float, z: Float): FloatArray {
        return floatArrayOf(
                Math.toRadians(x.toDouble()).toFloat(),
                Math.toRadians(y.toDouble()).toFloat(),
                Math.toRadians(z.toDouble()).toFloat()
        )

    }

}
