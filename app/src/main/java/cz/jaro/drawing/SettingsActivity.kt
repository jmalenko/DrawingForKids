package cz.jaro.drawing

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_settings.*
import java.io.File

class SettingsActivity : AppCompatActivity(), MyPurchasesListener {

    private val tag = SettingsActivity::class.java.name

    private lateinit var myPurchases: MyPurchases

    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        myPurchases = MyPurchases(this, this)
        if (!myPurchases.isPremium()) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        }

        // Update content
        updateSavedDrawingsVisiblity()

        grantPermissions.setOnClickListener { onSavedGrandPermission() }

        val picturesDir = DrawingActivity.constructPicturesDir()
        savedDirectoryText.text = picturesDir.toString()
        viewSavedButton.setOnClickListener { onViewSavedButtonClick(picturesDir) }

        buyButton.setOnClickListener { onBuyButtonClick() }
        buyButton.visibility = View.GONE
        priceText.visibility = View.GONE
        premiumVersionText.visibility = View.GONE

        feedbackButton.setOnClickListener { onFeedbackButtonClick() }
    }

    override fun onDestroy() {
        super.onDestroy()

        myPurchases.endBillingClient()
    }

    private fun onViewSavedButtonClick(picturesDir: File) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(Uri.fromFile(picturesDir), "image/*")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun updateSavedDrawingsVisiblity() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            noPermissions.visibility = View.VISIBLE
            hasPermissions.visibility = View.GONE
        } else {
            noPermissions.visibility = View.GONE
            hasPermissions.visibility = View.VISIBLE
        }
    }

    private fun onSavedGrandPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), DrawingActivity.PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            DrawingActivity.PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                updateSavedDrawingsVisiblity()
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun onFeedbackButtonClick() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getString(R.string.feedback_title))
        builder.setCancelable(false)

        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.hint = resources.getString(R.string.feedback_hint)

        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton(getString(R.string.send)) { dialog: DialogInterface, i: Int ->
            if (myPurchases.isPremium()) {
                firebaseAnalytics = FirebaseAnalytics.getInstance(this)
            }

            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "feedback")
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, input.text.toString())
            firebaseAnalytics!!.logEvent(FirebaseAnalytics.Event.ADD_TO_WISHLIST, bundle)

            Toast.makeText(this, resources.getString(R.string.feedback_thank_you), Toast.LENGTH_SHORT).show()

            if (myPurchases.isPremium()) {
                firebaseAnalytics = null
            }
        }
        builder.setNegativeButton(R.string.cancel) { dialog: DialogInterface, i: Int ->
            dialog.cancel()
        }

        val dialog = builder.create()

        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) // Show soft keyboard

        dialog.show()
    }

    private fun onBuyButtonClick() {
        myPurchases.buy(MyPurchases.SKU_PREMIUM_VERSION)
    }

    private fun updateBuyVisibility() {
        if (myPurchases.isPremium()) {
            buyButton.visibility = View.GONE
            priceText.visibility = View.GONE
            premiumVersionText.visibility = View.VISIBLE
        } else {
            buyButton.visibility = View.VISIBLE
            priceText.visibility = View.VISIBLE
            premiumVersionText.visibility = View.GONE
        }
        billingClientStatus.visibility = View.GONE
    }

    override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
        if (billingResponseCode == BillingClient.BillingResponse.OK) {
            updateBuyVisibility()
        } else {
            billingClientStatus.text = getString(R.string.billing_setup_error, MyPurchases.billingResponseCodeToString(billingResponseCode))
            // TODO If the billing client is unavailable, maybe we should default to premium version (for some period of time)
        }
    }

    override fun onBillingServiceDisconnected() {
        billingClientStatus.text = getString(R.string.billing_service_disconnected)
    }

    override fun skuDetailsUpdated(responseCode: Int, skuDetailsMap: Map<String, SkuDetails>) {
        if (responseCode == BillingClient.BillingResponse.OK) {
            val premiumVersion = skuDetailsMap[MyPurchases.SKU_PREMIUM_VERSION]
            if (premiumVersion != null) {
                priceText.text = getString(R.string.text_premium_version_4, premiumVersion.price)
            } else {
                Log.w(tag, "Cannot get the price of premium version.")
                priceText.text = getString(R.string.billing_querySkuDetailsAsync_error)
            }
        }
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        if (!myPurchases.isPremium()) {
            if (purchases != null) {
                for (purchase in purchases) {
                    val bundle = Bundle()

                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "onPurchasesUpdated")
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, purchase.sku)
                    bundle.putString(FirebaseAnalytics.Param.CREATIVE_NAME, MyPurchases.billingResponseCodeToString(responseCode))

                    bundle.putLong(FirebaseAnalytics.Param.SUCCESS, if (responseCode == BillingClient.BillingResponse.OK) 1 else 0)

                    firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.ADD_TO_CART, bundle)
                }
            }
        }

        updateBuyVisibility()

        if (purchases != null) {
            val skuDetailsMap = purchases.associateBy({ it.sku }, { it })
            if (skuDetailsMap[MyPurchases.SKU_PREMIUM_VERSION] != null) {
                // TODO Restart all the activities such that the non-tracking applies immediately
            }
        }
    }

}