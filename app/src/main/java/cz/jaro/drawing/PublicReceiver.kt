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
                // Start the activity. The activity's onNewIntent(Intent) method is called in which we register the next event.
                Log.d(tag, "Starting activity (if needed)")

                val intent = Intent(context, DrawingActivity::class.java)
                intent.action = DrawingActivity.ACTION_KEEP
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            else -> {
                // Forward the intent to DrawingActivity
                val localBroadcastManager = LocalBroadcastManager.getInstance(context)
                localBroadcastManager.sendBroadcast(intent)
            }
        }
    }
}
