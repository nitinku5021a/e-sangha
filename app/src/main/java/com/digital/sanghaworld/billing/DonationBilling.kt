package com.digital.sanghaworld.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DonationTier(
    val productId: String,
    val title: String,
    val multiplier: Int,
    val productDetails: ProductDetails?,
    val unitPriceLabel: String,
    val priceMicros: Long,
    val currencyCode: String,
    val isAvailable: Boolean
) {
    fun totalLabel(quantity: Int): String {
        if (quantity <= 0 || priceMicros <= 0) return unitPriceLabel
        return formatMoney(priceMicros * quantity, currencyCode)
    }
}

class DonationViewModel(application: Application) : AndroidViewModel(application), PurchasesUpdatedListener {

    private val _tiers = MutableStateFlow<List<DonationTier>>(emptyList())
    val tiers: StateFlow<List<DonationTier>> = _tiers.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val billingClient = BillingClient.newBuilder(application)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    viewModelScope.launch {
                        refreshProducts()
                        consumeOutstandingPurchases()
                    }
                } else {
                    _loading.value = false
                    _status.value = "Google Play billing is unavailable on this device."
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun reload() {
        viewModelScope.launch { refreshProducts() }
    }

    fun purchase(activity: Activity, productId: String) {
        val tier = _tiers.value.firstOrNull { it.productId == productId && it.isAvailable } ?: return
        val details = tier.productDetails ?: return
        val offerToken = oneTimeOfferToken(details)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                if (!offerToken.isNullOrBlank()) {
                    setOfferToken(offerToken)
                }
            }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            _status.value = "Could not open Google Play. ${result.debugMessage}".trim()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                viewModelScope.launch { consumePurchases(purchases.orEmpty()) }
            }
            BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                _status.value = billingResult.debugMessage.ifBlank { "Purchase could not be completed." }
            }
        }
    }

    override fun onCleared() {
        billingClient.endConnection()
        super.onCleared()
    }

    private suspend fun refreshProducts() {
        _loading.value = true
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                TIER_DEFINITIONS.map { def ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(def.productId)
                        .setProductType(ProductType.INAPP)
                        .build()
                }
            )
            .build()
        if (!billingClient.isReady) {
            _loading.value = false
            _tiers.value = emptyList()
            _status.value = "Google Play Billing is not ready yet. Open Donate again in a moment."
            return
        }
        val result = billingClient.queryProductDetails(params)
        _loading.value = false
        val playResult = result.billingResult
        if (playResult.responseCode != BillingResponseCode.OK) {
            _tiers.value = emptyList()
            _status.value = "Could not load donation products from Google Play (${playResult.responseCode}): ${playResult.debugMessage.ifBlank { "unknown error" }}"
            return
        }
        val detailsById = result.productDetailsList.orEmpty().associateBy { it.productId }
        val tiers = TIER_DEFINITIONS.map { def ->
            val product = detailsById[def.productId]
            val offer = product?.let { firstOffer(it) }
            DonationTier(
                productId = def.productId,
                title = def.title,
                multiplier = def.multiplier,
                productDetails = product,
                unitPriceLabel = offer?.formattedPrice ?: def.unavailableLabel,
                priceMicros = offer?.priceAmountMicros ?: 0L,
                currencyCode = offer?.priceCurrencyCode.orEmpty(),
                isAvailable = product != null && offer != null
            )
        }
        _tiers.value = tiers
        val missing = tiers.filterNot { it.isAvailable }.map { it.title }
        _status.value = if (missing.isNotEmpty()) {
            "Set up these Play Console donation products to unlock the bundles: ${missing.joinToString(", ")}."
        } else {
            null
        }
    }

    private suspend fun consumeOutstandingPurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
        )
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            consumePurchases(result.purchasesList)
        }
    }

    private suspend fun consumePurchases(purchases: List<Purchase>) {
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            val consume = billingClient.consumePurchase(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
            if (consume.billingResult.responseCode == BillingResponseCode.OK) {
                _status.value = "Thank you for supporting this app."
            }
        }
    }

    private fun firstOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
        val list = details.oneTimePurchaseOfferDetailsList
        if (!list.isNullOrEmpty()) return list.first()
        return details.oneTimePurchaseOfferDetails
    }

    private fun oneTimeOfferToken(details: ProductDetails): String? {
        return firstOffer(details)?.offerToken
    }

    companion object {
        const val MAX_QUANTITY = 10
        private val TIER_DEFINITIONS = listOf(
            TierDefinition("donation_unit_1x", "1X", 1, "Activate the 1X product in Play Console."),
            TierDefinition("donation_unit_10x", "10X", 10, "Activate the 10X product in Play Console."),
            TierDefinition("donation_unit_100x", "100X", 100, "Activate the 100X product in Play Console.")
        )
    }
}

private data class TierDefinition(
    val productId: String,
    val title: String,
    val multiplier: Int,
    val unavailableLabel: String
)

private fun formatMoney(micros: Long, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance()
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(micros / 1_000_000.0)
}