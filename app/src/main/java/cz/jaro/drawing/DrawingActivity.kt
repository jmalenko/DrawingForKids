package cz.jaro.drawing

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import cz.jaro.drawing.DrawingActivity.Companion.vectorInRadToStringInDeg
import kotlinx.android.synthetic.main.activity_drawing.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI

/**
 * An activity that prevents interaction with outside of the app. Specifically:
 * - Fullscreen activity (sticky immersive mode), single instance
 * - Contain only the drawing view
 * - Prevent screen rotation
 * - Keep screen on (but user can turn it off by pressing power button)
 * - If the screen is turned off by pressing the power button, then 1. turn the screen on (unreliable based on testing) and 2. don't require password/PIN (reliable after pressing the power button again)
 * - Prevent all keys/buttons, including Volume Up/Down, Back, Recent Apps
 * - Clear the image if the orientation changes by more than 90 degrees (and back) in one second
 * - A notification exists during the life of the activity - for quitting the app
 * - Bring the app to front regularly every 3 seconds. Useful when the user presses the Home key (on the navigation bar).
 * - Save the image on quit and before it is cleared
 *
 * The activity can be quit (only) byt the following
 * - 1. Pull down the status bar (needs two swipes as the app is in fullscreen sticky immersive mode), 2. press the Quit action in the notification
 * - 1. Press Home key, 2. press Recent Apps key, 3. swipe the app
 *
 * What is not prevented:
 * - The status bar and navigation bar cannot be removed. The interaction is minimized by sticky immersive mode and blocking the Apps and Back (not Home)
 *   buttons in navigation bar.
 * - Power button. This includes both short press (to turn off the screen) and long press with menu to power off the phone.
 */
class DrawingActivity : Activity(), SensorEventListener {

    private val tag = DrawingActivity::class.java.name

    private lateinit var keyguardLock: KeyguardManager.KeyguardLock

    private lateinit var sensorManager: SensorManager
    val sensorRecords: RecentList<OrientationRecord> = RecentList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start components

        // Disable keyguard - to NOT require password on lockscreen (and generally omit the lockscreen)
        disableKeyguard()

        // Listen for local intents
        val filter = IntentFilter(ACTION_QUIT)
        filter.addAction(ACTION_KEEP)
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(receiver, filter)

        // Create notification
        createNotification()

        // Start keeper
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        registerKeeper()

        // Start sensor
        startSensors()
    }

    override fun onResume() {
        super.onResume()

        // It can happen that the activity was started (from PublicReceiver), and took 2 seconds to appear (run this function). If the app was quited in this period, quit the app.
        if (PublicReceiver.quitRecently(this)) {
            Log.w(tag, "The app quited in recent past. Not resuming activity.")
            finish()
        }

        // Fullscreen - sticky immersive mode
        // This is intentionally here to prevent status bar from appearing in certain situations.
        // Specifically without this, the status bar would appear after 1. leaving and returning to the app (but this could be solved by entering the immersive
        // mode again in onWindowFocusChanged() ) and 2. after pressing power button (to turn off the screen) and pressing the power button again (to return to
        // the app, possibly with unlocking) the status bar was visible.
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onPause() {
        super.onPause()

        // This immediately returns to the app after pressing the Recent Apps key
        // However, this method is also called when 1. the Home key is pressed or when 2. system alarm is registered (for keeper). In these instances the following code has no effect.
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.moveTaskToFront(taskId, 0)
    }

    override fun onDestroy() {
        super.onDestroy()

        saveDrawing()

        // Stop components (in reverse order compared to onCreate() )

        stopSensors()

        cancelKeeper()

        cancelNotification()

        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.unregisterReceiver(receiver)

        enableKeyguard()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block all keys, including keyCode = KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN
        Log.i(tag, "Blocked key ${keyCodeToString(keyCode)} (keyCode=$keyCode)")
        return true
    }

    private fun keyCodeToString(action: Int): String {
        return when (action) {
            KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume down"
            else -> "?"
        }
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v(tag, "onReceive() action=${intent.action}")

            when (intent.action) {
                ACTION_QUIT -> {
                    Log.i(tag, "Quiting")
                    finish()
                }
                // ACTION_KEEP was handled in PublicReceiver
                else -> {
                    throw IllegalArgumentException("Unexpected argument ${intent.action}")
                }
            }
        }
    }

    private fun saveDrawing() {
        // Construct the file name
        // Inspired by https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/src/com/android/systemui/screenshot/GlobalScreenshot.java#L138
        val imageTime = System.currentTimeMillis()
        val imageDate = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(imageTime))
        val imageFileName = String.format(DRAWING_FILE_NAME_TEMPLATE, imageDate)

        // Save to external storage
        if (isExternalStorageWritable()) {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DRAWING_DIR_NAME)

            // Create the directory (relevant only the first time)
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val imageFilePath = File(picturesDir, imageFileName).absolutePath

            // Save bitmap to file
            try {
                FileOutputStream(imageFilePath).use { out ->
                    canvas.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.i(tag, "Image saved to $imageFilePath")
            } catch (e: IOException) {
                Log.w(tag, "Cannot save image to $imageFilePath", e)
            }
        } else {
            Log.e(tag, "Cannot save image because the external storage is not writable")
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /*
     * Notification - allows quiting the app
     * =====================================
     */

    @SuppressLint("PrivateResource")
    private fun createNotification() {
        createNotificationChannel()

        val intent = Intent(this, DrawingActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val quitIntent = Intent(this, PublicReceiver::class.java).apply {
            action = ACTION_QUIT
        }
        val quitPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, quitIntent, 0)

        val mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.abc_ic_clear_material, getString(R.string.notification_main_action_quit), quitPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder.setContentTitle(getString(R.string.notification_main_text))
        } else {
            mBuilder.setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.notification_main_text))
        }

        // TODO Disable sound when the notification appears

        with(NotificationManagerCompat.from(this)) {
            notify(NOTIFICATION_MAIN_ID, mBuilder.build())
        }
    }

    private fun cancelNotification() {
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_MAIN_ID)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /*
     * Keeper - brings the app to front
     * ================================
     */

    private fun registerKeeper() {
        registerKeeper(tag, this)
    }

    private fun cancelKeeper() {
        Log.i(tag, "Cancelling keeper")
        keeperIntent.cancel()
    }

    /*
     * Disable keyguard - to NOT require password on lockscreen (and generally omit the lockscreen)
     * ============================================================================================
     */

    private fun disableKeyguard() {
        // TODO Requirement: When the power button is pressed (and screen turns off), then turn on the screen (without the keyguard). However, testing on Samsung Galaxy S7 shows that this works only once; afterwards the screen must be turned on by pressing the power button.

        // Make sure the activity is visible after the screen is turned on when the lockscreen is up. Also required to turn screen on when
        // KeyguardManager.requestDismissKeyguard(...) is called.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // TODO This block is untested
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            window.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        // Disable keyguard
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardLock = keyguardManager.newKeyguardLock(Context.KEYGUARD_SERVICE)
        keyguardLock.disableKeyguard()
    }

    private fun enableKeyguard() {
        keyguardLock.reenableKeyguard()
        // Note that the user may need to enter password/PIN after exiting from the app
    }

    /*
     * Sensors - to clear the drawing
     * ==============================
     */

    private fun startSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (sensor == null) {
            Log.w(tag, "Cannot monitor orientation changes (sensors missing)")
            // TODO Consider adding the "Clear" action to the notification (the only way o clear the canvas is to quit the app and start it again)
            return
        }

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {
        // Do nothing
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!canBeTrusted(event.accuracy))
            return

        // Add the event to list of events
        addEvent(event)

        // Check if the gesture was performed
        if (gesturePerformed()) {
            onGesture()
        }
    }

    private fun addEvent(event: SensorEvent): Boolean {
        // Add the event to the list
        val now = event.timestamp // nanoseconds since the device started
        val orientation = rotationSensorValuesToOrientations(event.values)
        val record = OrientationRecord(now, orientation)

        sensorRecords.add(record)

        // Remove old records from the list
        while (SENSOR_HISTORY_NS < now - sensorRecords[0].timestamp) {
            sensorRecords.removeFirst()
        }

        return true
    }

    /**
     * Detect the intended orientation change.
     * The intended orientation change is defined by: rotating the device substantially away and back to the original position, in a short period of time.
     */
    fun gesturePerformed(): Boolean {
        /*
        Algorithm:
        We go back and compare the current orientation with the past.
        First, we are looking for an orientation that is different by more than SENSOR_ANGLE_OUT_RAD from the current orientation.
        Second, we are looking for an orientation that is different by less than SENSOR_ANGLE_NEAR_RAD from the current orientation.
        */
        if (sensorRecords.isEmpty())
            return false

        val o1 = sensorRecords[sensorRecords.size - 1].orientations
        var logMessage = "From ${vectorInRadToStringInDeg(o1)}"
        logMessage += "\nsize = ${sensorRecords.size}"

        var state = 1
        loop@ for (i in sensorRecords.size - 2 downTo 0) {
            val o2 = sensorRecords[i].orientations

            val angle = angleBetweenOrientations(o1, o2)
            logMessage += "\nTo ${vectorInRadToStringInDeg(o2)} is ${Math.round(Math.toDegrees(angle))} deg"

            val o3 = sensorRecords[i + 1].orientations
            val angle2 = angleBetweenOrientations(o3, o2)
            if (Math.PI / 9 < angle2)
                logMessage += "  jump ${Math.round(Math.toDegrees(angle2))} deg"

            when (state) {
                1 -> {
                    if (SENSOR_ANGLE_OUT_RAD < angle) {
                        logMessage += "\nBig difference found"
                        state++
                    }
                }
                2 -> {
                    if (angle < SENSOR_ANGLE_NEAR_RAD) {
                        logMessage += "\nSmall difference found"
                        state++
                        break@loop
                    }
                }
            }
        }

//        Log.d(tag, logMessage)
//        logText.text = logMessage

        return state == 3
    }

    private fun onGesture() {
        Log.i(tag, "Clearing the image")

        vibrate()

        saveDrawing()

        // Clear the canvas
        canvas.clear()

        // Remove all the sensorAcc values
        sensorRecords.clear()
    }

    fun angleBetweenOrientations(o1: FloatArray, o2: FloatArray): Double {
        val orientationAngle = orientationAngle(o1, o2)
        val rollAngle = rollAngle(o1, o2)
        val angle = Math.max(orientationAngle, rollAngle)
        return angle
    }

    private fun rotationSensorValuesToOrientations(values: FloatArray): FloatArray {
        // The rotation vector sensor combines raw data generated by the gyroscope, accelerometer, and magnetometer to create a quaternion (the values parameter)

        // Convert the quaternion into a rotation matrix (a 4x4 matrix)
        val rotationMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)

        // Remap coordinate system
        val remappedRotationMatrix = FloatArray(16)
        SensorManager.remapCoordinateSystem(rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedRotationMatrix)

        // Convert to orientations (in radians)
        val orientations = FloatArray(3)
        SensorManager.getOrientation(remappedRotationMatrix, orientations)

        // Convert to orientations in degrees
//        val orientationsDeg = FloatArray(3)
//        for (i in 0..2) {
//            orientationsDeg[i] = Math.toDegrees(orientations[i].toDouble()).toFloat()
//        }

        return orientations
    }
    /**
     * Returns the angle between [orientations [o1] and [o2]. Ignores the roll.
     * @param o1 Orientation, in radians.
     * @param o2 Orientation, in radians.
     * @return Angle in radians.
     */
    private fun orientationAngle(o1: FloatArray, o2: FloatArray): Double {
        // Source: https://en.wikipedia.org/wiki/Great-circle_distance

        val delta0 = Math.abs(o1[0] - o2[0])

        val addSin = Math.sin(o1[1].toDouble()) * Math.sin(o2[1].toDouble())
        val addCos = Math.cos(o1[1].toDouble()) * Math.cos(o2[1].toDouble()) * Math.cos(delta0.toDouble())

        val add = addSin + addCos

        val angle = Math.acos(add)
        return angle
    }

    /**
     * Returns the roll between [orientations [o1] and [o2]. Ignores the azimuth and pitch.

     * @param o1 Orientation, in radians.
     * @param o2 Orientation, in radians.
     * @return Angle in radians.
     */
    private fun rollAngle(o1: FloatArray, o2: FloatArray): Double {
        val delta2 = o1[2] - o2[2]
        val angle = Math.abs(delta2.toDouble())
        return angle
    }

    private fun canBeTrusted(accuracy: Int): Boolean {
        return accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH ||
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ||
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (v != null && v.hasVibrator()) {
            val duration = 200L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(duration)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "MAIN_NOTIFICATION"
        const val NOTIFICATION_MAIN_ID = 0
        const val KEEPER_INTERVAL_SEC = 3

        const val SENSOR_HISTORY_NS = 1_500_000_000
        const val SENSOR_ANGLE_OUT_RAD = 90 / 180f * PI
        const val SENSOR_ANGLE_NEAR_RAD = 20 / 180f * PI

        const val ACTION_QUIT = "ACTION_QUIT"
        const val ACTION_KEEP = "ACTION_KEEP"

        const val DRAWING_DIR_NAME = "DrawingForKids"
        const val DRAWING_FILE_NAME_TEMPLATE = "%s.png"

        private var alarmManager: AlarmManager? = null
        private lateinit var keeperIntent: PendingIntent

        fun registerKeeper(tag: String, context: Context) {
            val now = GregorianCalendar()
            val target = now.clone() as Calendar
            target.add(Calendar.SECOND, KEEPER_INTERVAL_SEC)
            target.set(Calendar.MILLISECOND, 0)

            keeperIntent = Intent(context, PublicReceiver::class.java).let { intent ->
                intent.action = ACTION_KEEP
                PendingIntent.getBroadcast(context, 0, intent, 0)
            }

            if (alarmManager != null) {
                Log.i(tag, "Registering keeper")

                setSystemAlarm(alarmManager!!, target, keeperIntent)
            } else {
                Log.w(tag, "Cannot register keeper")
            }
        }

        /**
         * Register system alarm that works reliably - triggers on a specific time, regardless the Android version, and whether the device is asleep (in low-power
         * idle mode).
         *
         * @param alarmManager AlarmManager
         * @param time         Alarm time
         * @param intent       Intent to run on alarm time
         */
        private fun setSystemAlarm(alarmManager: AlarmManager, time: Calendar, intent: PendingIntent) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            } else if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            }
        }

        fun vectorInRadToStringInDeg(v: FloatArray): String {
            return "[${Math.round(Math.toDegrees(v[0].toDouble()))}, ${Math.round(Math.toDegrees(v[1].toDouble()))}, ${Math.round(Math.toDegrees(v[2].toDouble()))}]"
        }
    }
}

class OrientationRecord(val timestamp: Long, val orientations: FloatArray) {
    override fun toString(): String {
        return vectorInRadToStringInDeg(orientations) + " @$timestamp"
    }
}