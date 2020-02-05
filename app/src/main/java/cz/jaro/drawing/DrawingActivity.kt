package cz.jaro.drawing

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.google.firebase.analytics.FirebaseAnalytics
import cz.jaro.drawing.DrawingActivity.Companion.vectorInRadToStringInDeg
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_drawing.*
import kotlinx.android.synthetic.main.activity_drawing_debug.*
import java.io.*
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
 * - Bring the app to front regularly every 3 seconds. Useful when the user presses the Home key (on the navigation bar). Also includes collapsing the expanded notification drawer
 * - Save the image on quit and before it is cleared
 * - On first start, show a dialog explaining how to quit the app
 *
 * The activity can be quit (only) byt the following
 * - 1. Pull down the status bar (needs two swipes as the app is in fullscreen sticky immersive mode), 2. press the Quit action in the notification
 * - 1. Press Home key, 2. press Recent Apps key, 3. swipe the app
 *
 * What is not prevented:
 * - The status bar and navigation bar cannot be removed. The interaction is minimized by sticky immersive mode and blocking the Apps and Back (not Home)
 *   buttons in navigation bar.
 * - Power button. This includes both short press (to turn off the screen) and long press with menu to power off the phone.
 *
 * Technical notes:
 * - We use two approaches to detect gesture.
 *   1. "Game rotation vector" sensor - can detect turn in all 3 dimensions. But the sensor may not be available on all devices (specifically, it is not available on Amazon Kindle Fire HD).
 *   2. Orientation event listener - can detect only turns around the Z axis. Is supported by most devices (including Amazon Kindle Fire HD).
 */
class DrawingActivity : AppCompatActivity(), SensorEventListener, View.OnSystemUiVisibilityChangeListener {

    private val tag = DrawingActivity::class.java.name

    private lateinit var keyguardLock: KeyguardManager.KeyguardLock

    private lateinit var sensorManager: SensorManager
    private lateinit var orientationListener: OrientationEventListener
    val sensorRecords: RecentList<OrientationRecord> = RecentList()
    private var isGameSensorUsed = false
    private var isOrientationListenerUsed = false

    private lateinit var myPurchases: MyPurchases

    private var bitmapToSave: Bitmap? = null // Contains the bitmap to be saved just after the user grants permission

    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = if (isDebug()) R.layout.activity_drawing_debug else R.layout.activity_drawing
        setContentView(layout)

        window.decorView.setOnSystemUiVisibilityChangeListener(this)

        // Keep the screen on
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide action bar
        val actionBar = supportActionBar
        actionBar?.hide()

        myPurchases = MyPurchases(this)
        if (myPurchases.isPremium()) {
            log(Log.INFO, "Premium version")

            // Disable Crashlytics
            val crashlyticsKit = Crashlytics.Builder()
                    .core(CrashlyticsCore.Builder().disabled(true).build())
                    .build()
            // Initialize Fabric with the debug-disabled Crashlytics
            Fabric.with(this, crashlyticsKit)
        } else {
            log(Log.INFO, "Standard version")

            // Enable Analytics
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        }

        // Start components

        // Disable keyguard - to NOT require password on lockscreen (and generally omit the lockscreen)
        disableKeyguard()

        // Listen for local intents
        val filter = IntentFilter(ACTION_QUIT)
        filter.addAction(ACTION_KEEP)
        filter.addAction(ACTION_CLEAR)
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(receiver, filter)

        // Start keeper
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        registerKeeper()

        // Start sensor
        startGestureDetectorBySensor()
        startGestureDetectorByOrientationListener()

        // Create notification
        startNotification()

        // On first run...
        checkFirstRun()
    }

    override fun onResume() {
        super.onResume()

        // It can happen that the activity was started (from PublicReceiver), and took 2 seconds to appear (run this function). If the app was quited in this period, quit the app.
        if (PublicReceiver.quitRecently(this)) {
            log(Log.WARN, "The app quited in recent past. Not resuming activity.")
            finish()
        }

        // Fullscreen - sticky immersive mode
        // This is intentionally here to prevent status bar from appearing in certain situations.
        // Specifically without this, the status bar would appear after 1. leaving and returning to the app (but this could be solved by entering the immersive
        // mode again in onWindowFocusChanged() ) and 2. after pressing power button (to turn off the screen) and pressing the power button again (to return to
        // the app, possibly with unlocking) the status bar was visible.
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or // hide nav bar
                        View.SYSTEM_UI_FLAG_FULLSCREEN or // hide status bar
                        View.SYSTEM_UI_FLAG_IMMERSIVE
    }

    override fun onSystemUiVisibilityChange(visibility: Int) {
        log(Log.DEBUG, "onSystemUiVisibilityChange(visibility=$visibility)")
        if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
            // The status bar and navigation bar are visible.
            canvas.onBarsAppeared()
        }
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

        stopNotification()

        stopGestureDetectorByOrientationListener()
        stopGestureDetectorBySensor()

        cancelKeeper()

        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.unregisterReceiver(receiver)

        enableKeyguard()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block all keys, including keyCode = KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN
        log(Log.INFO, "Blocked key ${keyCodeToString(keyCode)} (keyCode=$keyCode)")
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
            log(Log.VERBOSE, "onReceive() action=${intent.action}")

            when (intent.action) {
                ACTION_QUIT -> {
                    log(Log.INFO, "Quiting")
                    finish()
                }
                // ACTION_KEEP was handled in PublicReceiver
                ACTION_CLEAR -> {
                    saveAndClear()
                }
                else -> {
                    throw IllegalArgumentException("Unexpected argument ${intent.action}")
                }
            }
        }
    }

    /**
     * Checks if the device is Amazon Kindle Fire.
     *
     * The following website shows the limitation of Amazon Fire: https://developer.amazon.com/docs/app-submission/migrate-existing-app.html
     *
     * In this app, the following are relevant:
     * - disable_keyguard permissions are unsupported
     */
    private fun isKindleFire(): Boolean {
        return Build.MANUFACTURER == "Amazon" &&
                (Build.MODEL == "Kindle Fire" || Build.MODEL.startsWith("KF"))
    }

    /*
     * Logging
     * =======
     */

    @SuppressLint("SetTextI18n")
    fun log(priority: Int, msg: String, tr: Throwable? = null) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(cal.time)

        val priorityStr = when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }

        var message = "$timeStr $priorityStr $msg"

        if (tr != null) {
            // Print stack trace
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            tr.printStackTrace(pw)
            message += "\n$sw"
        }

        // Print to syslog
        Log.println(priority, tag, message)

        // Show on screen if debug
        if (isDebug()) {
            val lengthMax = 10000

            var textOld = logText.text
            if (lengthMax < textOld.length) textOld = textOld.subSequence(0, lengthMax)

            logText.text = "$message\n$textOld"
        }
    }

    private fun isDebug() = BuildConfig.BUILD_TYPE == "debug"

    /*
     * Save drawing
     * ============
     */

    private fun saveDrawing() {
        if (!canvas.isEmpty())
            if (checkPermission())
                saveDrawingWithPermission(canvas.getDrawingBitmap())
    }

    private fun saveDrawingWithPermission(bitmap: Bitmap) {
        // Save to external storage
        if (isExternalStorageWritable()) {
            // Directory that stores images
            val picturesDir = constructPicturesDir()

            // Create the directory (relevant only the first time)
            if (!picturesDir.exists()) {
                val created = picturesDir.mkdirs()
                if (!created) {
                    log(Log.WARN, "Cannot save image because the directory ($picturesDir) cannot be created")
                    return
                }
            }

            // Construct the file name
            // Inspired by https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/src/com/android/systemui/screenshot/GlobalScreenshot.java#L138
            val now = System.currentTimeMillis()
            val formattedDate = SimpleDateFormat(DRAWING_FILE_NAME_DATE_FORMAT, Locale.US).format(Date(now))
            val imageFileName = String.format(DRAWING_FILE_NAME_TEMPLATE, formattedDate)
            val imageFilePath = File(picturesDir, imageFileName).absolutePath

            // Save bitmap to file
            try {
                FileOutputStream(imageFilePath).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                log(Log.INFO, "Image saved to $imageFilePath")
            } catch (e: IOException) {
                log(Log.WARN, "Cannot save image to $imageFilePath", e)
            }
        } else {
            log(Log.WARN, "Cannot save image because the external storage is not writable")
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private fun checkPermission(): Boolean {
        // In API 23 (Marshmallow) and above, we need to request permissions at run time (because it's a dangerous permission).
        val thisActivity = this
        if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(thisActivity, resources.getString(R.string.toast_cannot_save), Toast.LENGTH_SHORT).show()
            } else {
                // Save the current drawing
                bitmapToSave = canvas.getDrawingBitmap()

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(thisActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)

                // PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE is an app-defined int constant. The callback method gets the result of the request.
            }
            return false
        } else {
            return true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted. Do the task you need to do.

                    // Save the current image
                    if (bitmapToSave != null) {
                        saveDrawingWithPermission(bitmapToSave!!)
                        bitmapToSave = null
                    }
                } else {
                    // Permission denied, Disable the functionality that depends on this permission.
                }
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    /*
     * Notification - allows quiting the app
     * =====================================
     */

    @SuppressLint("PrivateResource")
    private fun startNotification() {
        createNotificationChannel()

        val intent = Intent(this, DrawingActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val quitIntent = Intent(this, PublicReceiver::class.java).apply {
            action = ACTION_QUIT
        }
        val quitPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, quitIntent, 0)

        val settingsIntent = Intent(this, PublicReceiver::class.java).apply {
            action = ACTION_SETTINGS
        }
        val settingsPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, settingsIntent, 0)

        val mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_baseline_power_settings_new_24px, getString(R.string.notification_main_action_quit), quitPendingIntent)
        // TODO Always show the notification with expanded action buttons (currently unsupported by Android API)

        // Show clear action if the sensor is not used
        if (!isGestureSupported()) {
            val clearIntent = Intent(this, PublicReceiver::class.java).apply {
                action = ACTION_CLEAR
            }
            val clearPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, 0)

            mBuilder.addAction(R.drawable.ic_baseline_clear_24px, getString(R.string.notification_main_action_clear), clearPendingIntent)
        }

        mBuilder.addAction(R.drawable.ic_baseline_settings_20px, getString(R.string.notification_main_action_settings), settingsPendingIntent)

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

    private fun stopNotification() {
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
        log(Log.INFO, "Cancelling keeper")
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
        if (!isKindleFire()) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardLock = keyguardManager.newKeyguardLock(Context.KEYGUARD_SERVICE)
            keyguardLock.disableKeyguard()
        }
    }

    private fun enableKeyguard() {
        if (!isKindleFire()) {
            keyguardLock.reenableKeyguard()
            // Note that the user may need to enter password/PIN after exiting from the app
        }
    }

    /*
     * Sensor - to clear the drawing
     * =============================
     */

    private fun startGestureDetectorBySensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        isGameSensorUsed = sensor != null

        if (!isGameSensorUsed) {
            log(Log.DEBUG, "Cannot use game rotation sensor to detect orientation changes")
            return
        }

        log(Log.INFO, "Using game rotation sensor to detect orientation changes")

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopGestureDetectorBySensor() {
        sensorManager.unregisterListener(this)
        isGameSensorUsed = false
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {
        // Do nothing
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!canBeTrusted(event.accuracy))
            return

        // Add the event to list of events
        if (!addEventSensor(event))
            return

        // Check if the gesture was performed
        if (gesturePerformed()) {
            onGesture()
        }
    }

    private fun addEventSensor(event: SensorEvent): Boolean {
        val now = event.timestamp // nanoseconds since the device started
        val orientation = rotationSensorValuesToOrientations(event.values)
        val record = OrientationRecord(now, orientation)

        return addGestureRecordAndRemoveFarHistory(record)
    }

    private fun addGestureRecordAndRemoveFarHistory(record: OrientationRecord): Boolean {
        if (isDebug()) {
            if (!sensorRecords.isEmpty()) {
                val o1 = sensorRecords[sensorRecords.size - 1].orientations
                val o2 = record.orientations
                val angle = angleBetweenOrientations(o1, o2)
                if (angle < Math.PI / 18) // If there is a small change (less than 10 degrees)
                    return false
            }
        }

        sensorRecords.add(record)

        // Remove old records from the list
        val now = record.timestamp
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
            if (Math.PI / 9 < angle2) // If there is a significant jump
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

        log(Log.DEBUG, logMessage)

        return state == 3
    }

    private fun onGesture() {
        vibrate()

        saveAndClear()

        // Remove all the sensor values
        sensorRecords.clear()
    }

    private fun saveAndClear() {
        if (!myPurchases.isPremium()) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "clear")
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Clear")
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        }

        saveDrawing()

        log(Log.INFO, "Clearing the image")
        canvas.clear()
    }

    fun angleBetweenOrientations(o1: FloatArray, o2: FloatArray): Double {
        val orientationAngle = orientationAngle(o1, o2)
        return if (Math.abs(o1[1]) < Math.PI / 3) { // If the device is NOT in horizontal position, consider also the roll
            val rollAngle = rollAngle(o1, o2)
            Math.max(orientationAngle, rollAngle)
        } else {
            orientationAngle
        }
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

        // Index    What        Range in degrees
        // 0        Roll        -180 to 180
        // 1        Pitch       -90 to 90
        // 2        Azimuth     −180 to 180

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

        var angle = delta2.toDouble()
        if (angle < 0) angle += 2 * Math.PI
        if (Math.PI < angle) angle = 2 * Math.PI - angle

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
            val duration = 100L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(duration)
            }
        }
    }

    private fun isGestureSupported() = isGameSensorUsed || isOrientationListenerUsed

    /*
     * Orientation change detector - used to detect gesture when (game) sensor is not available
     * ========================================================================================
     */

    private fun startGestureDetectorByOrientationListener() {
        if (!isGameSensorUsed) {
            orientationListener = object : OrientationEventListener(applicationContext) {
                override fun onOrientationChanged(orientationDeg: Int) {
                    // Add the event to list of events
                    if (!addEventOrientation(orientationDeg))
                        return

                    // Check if the gesture was performed
                    if (gesturePerformed()) {
                        onGesture()
                    }
                }
            }

            if (!orientationListener.canDetectOrientation()) {
                log(Log.DEBUG, "Cannot use orientation listener to detect orientation changes")
                log(Log.WARN, "Cannot detect orientation changes")
                return
            }

            log(Log.INFO, "Using orientation listener to detect orientation changes")

            isOrientationListenerUsed = true
            orientationListener.enable()
        }
    }

    private fun stopGestureDetectorByOrientationListener() {
        if (isOrientationListenerUsed) {
            orientationListener.disable()
            isOrientationListenerUsed = false
        }
    }

    private fun addEventOrientation(angleDeg: Int): Boolean {
        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

        val now = System.currentTimeMillis() * 1_000_000
        val orientation = floatArrayOf(angleRad, 0f, 0f)
        val record = OrientationRecord(now, orientation)

        return addGestureRecordAndRemoveFarHistory(record)
    }

    /*
     * First run
     * =========
     */

    private fun checkFirstRun() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val firstRunCompleted = preferences.getBoolean(PREFERENCES_FIRST_RUN_COMPLETED, PREFERENCES_FIRST_RUN_COMPLETED__DEFAULT)
        if (!firstRunCompleted) {
            onFirstRunActionStart()
        }
    }

    private fun onFirstRunActionStart() {
        val alertDialog = AlertDialog.Builder(this).create()
        with(alertDialog) {
            setTitle(resources.getString(R.string.first_start_dialog__title))
            setMessage(resources.getString(R.string.first_start_dialog__message))
            setButton(AlertDialog.BUTTON_NEUTRAL, resources.getString(R.string.first_start_dialog__button)) { _, _ ->
                alertDialog.dismiss()
                onFirstRunActionEnd()
            }
            show()
        }
    }

    private fun onFirstRunActionEnd() {
        // Remember the the dialogue was displayed
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        preferences
                .edit()
                .putBoolean(PREFERENCES_FIRST_RUN_COMPLETED, !PREFERENCES_FIRST_RUN_COMPLETED__DEFAULT)
                .apply()
    }

    companion object {
        const val CHANNEL_ID = "MAIN_NOTIFICATION"
        const val NOTIFICATION_MAIN_ID = 0
        const val KEEPER_INTERVAL_SEC = 3

        const val SENSOR_HISTORY_NS = 1_500_000_000
        const val SENSOR_ANGLE_OUT_RAD = 89.5 / 180f * PI // Note: At business level, we say "turn by 90 degrees". At technical level, we use a slightly smaller threshold to correctly recognize the orientation changes by orientation listener (errors at the 7th decimal place).
        const val SENSOR_ANGLE_NEAR_RAD = 20 / 180f * PI

        const val ACTION_QUIT = "ACTION_QUIT"
        const val ACTION_KEEP = "ACTION_KEEP"
        const val ACTION_CLEAR = "ACTION_CLEAR"
        const val ACTION_SETTINGS = "ACTION_SETTINGS"
        const val ACTION_COLLAPSE_NOTIFICATION_DRAWER = "ACTION_COLLAPSE_NOTIFICATION_DRAWER"

        private const val DRAWING_DIR_NAME = "DrawingForKids"
        private const val DRAWING_FILE_NAME_DATE_FORMAT = "yyyyMMdd_HHmmss"
        private const val DRAWING_FILE_NAME_TEMPLATE = "%s.png"

        const val PREFERENCES_FIRST_RUN_COMPLETED = "FIRSTRUNCOMPLETED"
        const val PREFERENCES_FIRST_RUN_COMPLETED__DEFAULT = false

        internal const val PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1

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

        fun registerCollapseNotificationDrawer(tag: String, context: Context) {
            val now = GregorianCalendar()
            val target = now.clone() as Calendar
            target.add(Calendar.SECOND, KEEPER_INTERVAL_SEC) // Use the same interval as for keeper

            var intent = Intent(context, PublicReceiver::class.java).let { intent ->
                intent.action = ACTION_COLLAPSE_NOTIFICATION_DRAWER
                PendingIntent.getBroadcast(context, 0, intent, 0)
            }

            if (alarmManager != null) {
                Log.i(tag, "Registering collapse notification drawer")

                setSystemAlarm(alarmManager!!, target, intent)
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
            /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            } else*/ if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, intent)
            }
        }

        fun vectorInRadToStringInDeg(v: FloatArray): String {
            return "[${Math.round(Math.toDegrees(v[0].toDouble()))}, ${Math.round(Math.toDegrees(v[1].toDouble()))}, ${Math.round(Math.toDegrees(v[2].toDouble()))}]"
        }

        fun constructPicturesDir(): File {
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DRAWING_DIR_NAME)
        }
    }
}

class OrientationRecord(val timestamp: Long, val orientations: FloatArray) {
    override fun toString(): String {
        return vectorInRadToStringInDeg(orientations) + " @$timestamp"
    }
}