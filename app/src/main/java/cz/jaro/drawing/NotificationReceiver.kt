package cz.jaro.drawing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {

    private val TAG = NotificationReceiver::class.java.name

    override fun onReceive(context: Context, intent: Intent) {
        Log.e(TAG, "onReceive() action=${intent.action}")
        val action = intent.action

        when (action) {
            ACTION_QUIT -> {
                val local = Intent(ACTION_QUIT)
                val localBroadcastManager = LocalBroadcastManager.getInstance(context)
                localBroadcastManager.sendBroadcast(local)
            }
            else -> {
                throw IllegalArgumentException("Unexpected argument $action")
            }
        }
    }

    companion object {
        const val ACTION_QUIT: String = "ACTION_QUIT"
    }

}
