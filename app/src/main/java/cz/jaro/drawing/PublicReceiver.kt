package cz.jaro.drawing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

/**
 * This is a public BroadcastReceiver for the DrawingActivity. Most intents are forwarded to the activity.
 */
class PublicReceiver : BroadcastReceiver() {

    private val tag = PublicReceiver::class.java.name

    override fun onReceive(context: Context, intent: Intent) {
        Log.v(tag, "onReceive() action=${intent.action}")

        when (intent.action) {
            DrawingActivity.ACTION_KEEP -> {
                /*
                A note about the timings. Based on observations on Samsung Galaxy S7.

                The time between 1. the trigger time to which the alarm manager was set by alarmManager.setExactAndAllowWhileIdle(...) and 2. the execution of this onReceive(...) can be up to 2 seconds (in case the activity is NOT running).

                The time between 1. the startActivity(...) in this receiver and 2. onNewIntent(...) in the activity:
                A. If the activity is running, then it takes 0.05 seconds.
                B. If the activity is NOT running, then it takes up to 3 seconds. (The activity may not be running because e.g. the Home key was pressed recently.)

                   There is a risk in case B: during the 3 seconds, if the user starts another activity (another app), our app is never started; technically onNewIntent(...) is not executed.
                   Solution: Therefore we have to register the keeper here in PublicReceiver and not in DrawingActivity.
                */

                // Register for next time
                DrawingActivity.registerKeeper(tag, context)

                // Start the activity. The activity's onNewIntent(Intent) method is called in which we register the next event.
                val startIntent = Intent(context, DrawingActivity::class.java)
                startIntent.action = DrawingActivity.ACTION_KEEP
                startIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(startIntent)
            }
            else -> {
                // Forward the intent to DrawingActivity
                val localBroadcastManager = LocalBroadcastManager.getInstance(context)
                localBroadcastManager.sendBroadcast(intent)
            }
        }
    }
}
