package cz.jaro.drawing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_settings.*


class SettingsActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (!isPremium()) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        }

        val picturesDir = DrawingActivity.constructPicturesDir()

        savedDirectoryText.text = picturesDir.toString()

        viewSavedButton.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.setDataAndType(Uri.fromFile(picturesDir), "image/*")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        if (isStandardVersion()) {
            buyButton.visibility = View.VISIBLE
            versionText.visibility = View.GONE

            buyButton.setOnClickListener {
                // TODO Implement buy
            }
        } else {
            buyButton.visibility = View.GONE
            versionText.visibility = View.VISIBLE
        }

        feedbackButton.setOnClickListener {
            // TODO Implement feedback
        }
    }

    companion object {
        fun isStandardVersion(): Boolean {
            return false // TODO version check
        }

        fun isPremium(): Boolean {
            // TODO Implement payment
            return false
        }
    }
}