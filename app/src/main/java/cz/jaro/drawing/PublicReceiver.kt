package cz.jaro.drawing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.util.*

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

                // It can happen that the DrawingActivity.keeperIntent is not cancelled on quit. Therefore we keep track of quitting in this BroadcastReceiver.
                if (quitRecently(context)) {
                    Log.w(tag, "The app quited in recent past. Not invoking keeper.")
                    return
                }

                // Register for next time
                DrawingActivity.registerKeeper(tag, context)

                // Start the activity. The activity's onNewIntent(Intent) method is called in which we register the next event.
                val startIntent = Intent(context, DrawingActivity::class.java)
                startIntent.action = DrawingActivity.ACTION_KEEP
                startIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(startIntent)
            }
            DrawingActivity.ACTION_QUIT -> {
                doQuit(context, intent)
            }
            else -> {
                // Forward the intent to DrawingActivity
                val localBroadcastManager = LocalBroadcastManager.getInstance(context)
                localBroadcastManager.sendBroadcast(intent)
            }
        }
    }

    private fun doQuit(context: Context, intent: Intent) {
        saveQuitTime(context)

        // Forward the intent to DrawingActivity
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun saveQuitTime(context: Context) {
        val now = GregorianCalendar()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = preferences.edit()
        editor.putLong(PREF_QUIT_TIME, now.timeInMillis)
        editor.apply()
    }

    companion object {
        const val PREF_QUIT_TIME = "PREF_QUIT_TIME"
        const val PREF_DEFAULT_LONG = -1L

        // Constant derived as 5 seconds (the cold start is excessive) plus the interval of the keeper
        const val QUIT_RECENT_MS = 5000 + DrawingActivity.KEEPER_INTERVAL_SEC * 1000

        fun quitRecently(context: Context): Boolean {
            val now = GregorianCalendar()
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (preferences.contains(PREF_QUIT_TIME)) {
                val quitTimeMS = preferences.getLong(PREF_QUIT_TIME, PREF_DEFAULT_LONG)
                if (quitTimeMS != PREF_DEFAULT_LONG) {
                    val quitTime = GregorianCalendar()
                    quitTime.timeInMillis = quitTimeMS

                    val delta = now.timeInMillis - quitTime.timeInMillis
                    return delta < QUIT_RECENT_MS
                }
            }
            return false
        }


    }
}
