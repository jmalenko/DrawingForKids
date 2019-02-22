package cz.jaro.drawing

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_settings.*


class SettingsActivity : AppCompatActivity() {

    private var firebaseAnalytics: FirebaseAnalytics? = null

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

        if (isPremium()) {
            buyButton.visibility = View.GONE
            versionText.visibility = View.VISIBLE
        } else {
            buyButton.visibility = View.VISIBLE
            versionText.visibility = View.GONE

            buyButton.setOnClickListener {
                // TODO Implement buy
                Toast.makeText(this, "Buy", Toast.LENGTH_SHORT).show()
            }
        }

        feedbackButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getString(R.string.feedback_title))
            builder.setCancelable(false)

            // Set up the input
            val input = EditText(this)
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            input.hint = resources.getString(R.string.feedback_hint)

            builder.setView(input)

            // Set up the buttons
            builder.setPositiveButton("Send") { dialog: DialogInterface, i: Int ->
                if (isPremium()) {
                    firebaseAnalytics = FirebaseAnalytics.getInstance(this)
                }

                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "feedback")
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, input.text.toString())
                firebaseAnalytics!!.logEvent(FirebaseAnalytics.Event.ADD_TO_WISHLIST, bundle)

                Toast.makeText(this, resources.getString(R.string.feedback_thank_you), Toast.LENGTH_SHORT).show()

                if (isPremium()) {
                    firebaseAnalytics = null
                }
            }
            builder.setNegativeButton("Cancel") { dialog: DialogInterface, i: Int ->
                dialog.cancel()
            }

            builder.show()
        }
    }

    companion object {
        fun isPremium(): Boolean {
            // TODO Implement payment
            return false
        }
    }
}