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

/**
 * An activity that prevents interaction with outside of the app. Specifically:
 * - Fullscreen activity (sticky immersive mode), single instance
 * - Contain only the drawing view
 * - Prevent screen rotation
 * - Keep screen on
 * - Prevent volume buttons, back button, apps button
 * - A notification exists during the life of the activity
 *
 * The activity can be quit (only) byt the following
 * - 1. Pull down the status bar (needs two swipes as the app is in fullscreen immersive mode), 2. press the Quit action in the notification
 * - 1. Press Home key, 2. press Recent Apps key, 3. swipe the app
 */
class DrawingActivity : Activity() {

    private val tag = DrawingActivity::class.java.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)

        // Fullscreen - sticky immersive mode
        enableImmersiveMode()

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Listen for intents from the receivers
        val filter = IntentFilter(NotificationReceiver.ACTION_QUIT)
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(quitReceiver, filter)

        // Create notification
        createNotification()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onPause() {
        super.onPause()

        Log.i(tag, "Trying to block key Apps (if it was pressed, but this also called on Home key press)")
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.moveTaskToFront(taskId, 0)
    }

    override fun onDestroy() {
        super.onDestroy()

        cancelNotification()

        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.unregisterReceiver(quitReceiver)
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

    @SuppressLint("PrivateResource")
    private fun createNotification() {
        createNotificationChannel()

        val intent = Intent(this, DrawingActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val quitIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_QUIT
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

    private val quitReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    companion object {
        const val CHANNEL_ID: String = "MAIN_NOTIFICATION"
        const val NOTIFICATION_MAIN_ID: Int = 0
    }

}
