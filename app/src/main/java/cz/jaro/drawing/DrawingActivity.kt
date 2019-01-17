package cz.jaro.drawing

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import java.util.*

/**
 * An activity that prevents interaction with outside of the app. Specifically:
 * - Fullscreen activity (sticky immersive mode), single instance
 * - Contain only the drawing view
 * - Prevent screen rotation
 * - Keep screen on (but user can turn it off by pressing power button)
 * - If the screen is turned off by pressing the power button, then 1. turn the screen on (unreliable based on testing) and 2. don't require password/PIN (reliable after pressing the power button again)
 * - Prevent all keys/buttons, including Volume Up/Down, Back, Recent Apps
 * - A notification exists during the life of the activity - for quitting the app
 * - Bring the app to front regularly every 3 seconds. Useful when the user presses the Home key (on the navigation bar).
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
class DrawingActivity : Activity() {

    private val tag = DrawingActivity::class.java.name

    private var alarmManager: AlarmManager? = null
    private lateinit var keeperIntent: PendingIntent

    private lateinit var keyguardLock: KeyguardManager.KeyguardLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
    }

    override fun onResume() {
        super.onResume()

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

    override fun onNewIntent(intent: Intent?) {
        Log.v(tag, "onNewIntent() action=${intent?.action}")

        if (intent != null) {
            when (intent.action) {
                DrawingActivity.ACTION_KEEP -> {
                    // Try to dismiss keyguard (if the device is locked).
                    // Note: Works if keyguard is not secure or the device is currently in a trusted state.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // TODO This Block is untested
                        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        keyguardManager.requestDismissKeyguard(this, null)
                    }

                    // Register keeper for next period
                    registerKeeper()
                }
                else -> {
                    super.onNewIntent(intent)
                }
            }
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
                // ACTION_KEEP was handled in PublicReceiver and onNewIntent(Intent?)
                else -> {
                    throw IllegalArgumentException("Unexpected argument ${intent.action}")
                }
            }
        }
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
        val now = GregorianCalendar()
        val target = now.clone() as Calendar
        target.add(Calendar.SECOND, KEEPER_INTERVAL_SEC)

        val context = this
        keeperIntent = Intent(context, PublicReceiver::class.java).let { intent ->
            intent.action = ACTION_KEEP
            PendingIntent.getBroadcast(context, 0, intent, 0)
        }

        if (alarmManager != null) {
            Log.d(tag, "Registering keeper")
            setSystemAlarm(alarmManager!!, target, keeperIntent)
        } else {
            Log.w(tag, "Cannot register keeper")
        }
    }

    private fun cancelKeeper() {
        Log.i(tag, "Cancelling keeper")
        keeperIntent.cancel()
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

    companion object {
        const val CHANNEL_ID = "MAIN_NOTIFICATION"
        const val NOTIFICATION_MAIN_ID = 0
        const val KEEPER_INTERVAL_SEC = 3

        const val ACTION_QUIT = "ACTION_QUIT"
        const val ACTION_KEEP = "ACTION_KEEP"
    }

}
