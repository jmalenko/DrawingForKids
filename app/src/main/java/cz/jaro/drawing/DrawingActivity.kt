package cz.jaro.drawing

import android.app.Activity
import android.os.Bundle
import android.view.View

/**
 * A full-screen activity that prevents interaction with outside of the app. Specifically:
 * - Contains only the drawing view
 */
class DrawingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawing)

        // Fullscreen - sticky immersive mode
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

}
