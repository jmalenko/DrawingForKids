package cz.jaro.drawing

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import java.util.logging.Logger

/**
 * An activity that prevents interaction with outside of the app. Specifically:
 * - Fullscreen activity (sticky immersive mode)
 * - Contain only the drawing view
 * - Prevent screen rotation
 * - Prevent volume buttons
 */
class DrawingActivity : Activity() {

    val log = Logger.getLogger(DrawingActivity::class.java.name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)

        // Fullscreen - sticky immersive mode
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                log.info("Blocked key ${keyCodeToString(keyCode)}")
                // Do nothing
            }
        }
        return true;
    }

    private fun keyCodeToString(action: Int): String {
        return when (action) {
            KeyEvent.KEYCODE_VOLUME_UP -> "Volumne up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume down"
            else -> "?"
        }
    }

}
