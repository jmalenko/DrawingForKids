package cz.jaro.drawing

import android.app.Activity
import android.preference.PreferenceManager
import android.util.Log
import com.android.billingclient.api.*

/**
 * Class represents information about billing via Google Play. That includes whether or not the Premium version was bought.
 */
class MyPurchases(private val activity: Activity, private var listener: MyPurchasesListener? = null) : PurchasesUpdatedListener {

    private val tag = MyPurchases::class.java.name

    private lateinit var billingClient: BillingClient

    private var billingClientSetupResponseCode = SETUP_IN_PROGRESS

    private lateinit var skuDetailsMap: Map<String, SkuDetails>

    init {
        if (listener != null)
            setupBillingClient()
    }

    private fun setupBillingClient() {
        Log.d(tag, "setupBillingClient()")
        billingClient = BillingClient
                .newBuilder(activity)
                .setListener(this)
                .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                Log.d(tag, "onBillingSetupFinished()")
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    Log.i(tag, "Billing client setup finished with response code  ${billingResponseCodeToString(billingResponseCode)} ($billingResponseCode)")
                    updateSkuDetails()
                } else {
                    Log.w(tag, "Billing client setup finished with response code  ${billingResponseCodeToString(billingResponseCode)} ($billingResponseCode)")
                }

                billingClientSetupResponseCode = billingResponseCode

                listener?.onBillingSetupFinished(billingResponseCode)
            }

            override fun onBillingServiceDisconnected() {
                Log.d(tag, "onBillingServiceDisconnected()")
                Log.w(tag, "Billing client disconnected")

                billingClientSetupResponseCode = BillingClient.BillingResponse.SERVICE_DISCONNECTED

                listener?.onBillingServiceDisconnected()
            }
        })
    }

    fun endBillingClient() {
        Log.d(tag, "endBillingClient()")
        billingClient.endConnection()
    }

    private fun updateSkuDetails() {
        Log.d(tag, "updateSkuDetails()")
        val skuList = ArrayList<String>()
        skuList.add(SKU_PREMIUM_VERSION)

        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { responseCode, skuDetailsList ->
            Log.v(tag, "querySkuDetailsAsync() body")
            if (responseCode == BillingClient.BillingResponse.OK) {
                skuDetailsMap = skuDetailsList.associateBy({ it.sku }, { it })
            }
            listener?.skuDetailsUpdated(responseCode, skuDetailsMap)
        }
        Log.v(tag, "updateSkuDetails() finished")
    }

    fun buy(sku: String) {
        Log.d(tag, "buy($sku)")

        val skuDetails = skuDetailsMap[sku]

        val billingFlowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        Log.d(tag, "onPurchasesUpdated($responseCode, $purchases?)")

        // Check for the purchase of premium version
        if (responseCode == BillingClient.BillingResponse.OK) {
            if (purchases != null) {
                val skuDetailsMap = purchases.associateBy({ it.sku }, { it })
                if (skuDetailsMap[SKU_PREMIUM_VERSION] != null) {
                    val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
                    val editor = preferences.edit()

                    editor.putBoolean(PREF_PREMIUM_VERSION, true)

                    editor.apply()
                }
            }
        }

        listener?.onPurchasesUpdated(responseCode, purchases)
    }

    fun isPremium(): Boolean {
        Log.d(tag, "isPremium()")

        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val premiumVersion = preferences.getBoolean(PREF_PREMIUM_VERSION, PREF_PREMIUM_VERSION_DEFAULT)

        return premiumVersion
    }

    companion object {
        const val SKU_PREMIUM_VERSION = "premium_version"

        const val PREF_PREMIUM_VERSION = "PREF_PREMIUM_VERSION"
        const val PREF_PREMIUM_VERSION_DEFAULT = false

        const val SETUP_IN_PROGRESS = -1000 // Note: this must be a value that is not used in BillingClient.BillingResponse

        fun billingResponseCodeToString(billingResponseCode: Int): String {
            return when (billingResponseCode) {
                BillingClient.BillingResponse.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
                BillingClient.BillingResponse.OK -> "OK"
                BillingClient.BillingResponse.USER_CANCELED -> "USER_CANCELED"
                BillingClient.BillingResponse.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
                BillingClient.BillingResponse.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
                BillingClient.BillingResponse.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
                BillingClient.BillingResponse.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
                BillingClient.BillingResponse.ERROR -> "ERROR"
                BillingClient.BillingResponse.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
                BillingClient.BillingResponse.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
                else -> "Unknown"
            }
        }
    }
}

interface MyPurchasesListener : BillingClientStateListener, PurchasesUpdatedListener {
    fun skuDetailsUpdated(responseCode: Int, skuDetailsMap: Map<String, SkuDetails>)
}