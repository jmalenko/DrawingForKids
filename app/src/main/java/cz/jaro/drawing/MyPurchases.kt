package cz.jaro.drawing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Class represents information about billing via Google Play. That includes whether or not the Premium version was bought.
 */
class MyPurchases(private val activity: Activity, private var listener: MyPurchasesListener? = null) : PurchasesUpdatedListener {

    private val tag = MyPurchases::class.java.name

    private lateinit var billingClient: BillingClient

    private var billingClientSetupResponseCode = SETUP_IN_PROGRESS
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private lateinit var skuDetailsMap: Map<String, SkuDetails>

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        Log.d(tag, "setupBillingClient()")
        billingClient = BillingClient
                .newBuilder(activity)
                .setListener(this)
                .build()

        Log.v(tag, "starting connection")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                Log.d(tag, "onBillingSetupFinished()")
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    Log.i(tag, "Billing client setup finished with response code  ${billingResponseCodeToString(billingResponseCode)} ($billingResponseCode)")
                    updateSkuDetails()
                } else {
                    Log.w(tag, "Billing client setup finished with response code  ${billingResponseCodeToString(billingResponseCode)} ($billingResponseCode)")
                }

                Log.v(tag, "onBillingSetupFinished() before lock")
                lock.withLock {
                    Log.v(tag, "onBillingSetupFinished() in lock")
                    billingClientSetupResponseCode = billingResponseCode
                    condition.signalAll()
                }
                Log.v(tag, "onBillingSetupFinished() after lock")

                listener?.onBillingSetupFinished(billingResponseCode)
            }

            override fun onBillingServiceDisconnected() {
                Log.d(tag, "onBillingServiceDisconnected()")
                Log.w(tag, "Billing client disconnected")

                Log.v(tag, "onBillingServiceDisconnected() before lock")
                lock.withLock {
                    Log.v(tag, "onBillingServiceDisconnected() in lock")
                    billingClientSetupResponseCode = BillingClient.BillingResponse.SERVICE_DISCONNECTED
                    condition.signalAll()
                }
                Log.v(tag, "onBillingServiceDisconnected() after lock")

                listener?.onBillingServiceDisconnected()
            }
        })
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        Log.d(tag, "onPurchasesUpdated($responseCode, $purchases?)")

        listener?.onPurchasesUpdated(responseCode, purchases)
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

    fun isPremium(): Boolean {
        Log.d(tag, "isPremium()")
        // TODO The billingClient setup is asynchronous. But we often use the premium version check synchronously. That's why we wait here to finish the setup.. (TODO remove verbose logging)
//        lock.withLock {
//            Log.d(tag, "isPremium() in lock")
//            while (billingClientSetupResponseCode == SETUP_IN_PROGRESS) {
//                Log.v(tag, "isPremium(): Waiting for billing setup to finish...")
//                try {
//                    condition.await()
//                } catch (e: InterruptedException) {
//                    Log.w(tag, "Error while waiting  for billing setup to finish", e)
//                }
//            }
//        }
        Log.v(tag, "isPremium() after lock")

        Log.v(tag, "isPremium() continues after waiting for billing setup to finish")

        val purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)

        if (purchasesResult.purchasesList == null) // Needed for cases when we use billingClient before it's setup finished. In my experiments, the setup is finished synchronously if successful (although the method is asynchronous).
            return false

        val skuDetailsMap = purchasesResult.purchasesList.associateBy({ it.sku }, { it })
        if (skuDetailsMap[SKU_PREMIUM_VERSION] != null) {
            return true
        }

        return false
    }

    companion object {
        const val SKU_PREMIUM_VERSION = "premium_version"

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