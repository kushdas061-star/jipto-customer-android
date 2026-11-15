package com.jipto.customer.data.model

import kotlin.math.roundToInt

enum class ProductType {
    PACKAGED,
    LOOSE
}

enum class LooseWeightOption(val grams: Int, val displayLabel: String) {
    G_250(250, "250 g"),
    G_500(500, "500 g"),
    KG_1(1000, "1 kg"),
    KG_2(2000, "2 kg"),
    KG_3(3000, "3 kg"),
    KG_5(5000, "5 kg");

    companion object {
        fun fromGrams(grams: Int): LooseWeightOption =
            entries.find { it.grams == grams } ?: KG_1
    }
}

data class BannerItem(
    val bannerId: String = "",
    val imageUrl: String = "",
    val title: String = "",
    val subtitle: String = "",
    val offerBadge: String = "",
    val targetCategoryId: String = "",
    val active: Boolean = true,
    val displayOrder: Int = 1
)

data class Category(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val iconName: String = "",
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val imageUrl: String = "",
    val productType: ProductType = ProductType.PACKAGED,
    val mrp: Int = 0,              // For PACKAGED: unit MRP; For LOOSE: 1kg MRP
    val sellingPrice: Int = 0,     // For PACKAGED: unit price; For LOOSE: 1kg price
    val pricePerKg: Int = 0,       // Authoritative per-kg selling price for LOOSE items
    val mrpPerKg: Int = 0,         // Authoritative per-kg MRP for LOOSE items
    val unit: String = "1 packet", // e.g., "1 packet", "1 bottle", "1 kg"
    val stockGramsOrUnits: Int = 0,// Units for PACKAGED, total grams for LOOSE
    val isBestSeller: Boolean = false,
    val isTodayOffer: Boolean = false,
    val isPopular: Boolean = false,
    val isRecentlyAdded: Boolean = false,
    val isRecommended: Boolean = false,
    val isFrequentlyPurchased: Boolean = false,
    val isActive: Boolean = true
) {
    val discountPercentage: Int
        get() {
            val baseMrp = if (productType == ProductType.LOOSE && mrpPerKg > 0) mrpPerKg else mrp
            val baseSell = if (productType == ProductType.LOOSE && pricePerKg > 0) pricePerKg else sellingPrice
            if (baseMrp <= 0 || baseSell >= baseMrp) return 0
            return (((baseMrp - baseSell).toDouble() / baseMrp.toDouble()) * 100.0).roundToInt()
        }

    /**
     * Automatically calculates accurate selling price for loose grocery weights (min 250g).
     * Example: ₹60/kg -> 250g = ₹15, 500g = ₹30, 1kg = ₹60, 2kg = ₹120, 3kg = ₹180, 5kg = ₹300
     */
    fun calculatePriceForWeight(weightGrams: Int): Int {
        if (productType == ProductType.PACKAGED) return sellingPrice
        val safeGrams = weightGrams.coerceAtLeast(250)
        val perKg = if (pricePerKg > 0) pricePerKg else sellingPrice
        return ((perKg * safeGrams) / 1000.0).roundToInt()
    }

    fun calculateMrpForWeight(weightGrams: Int): Int {
        if (productType == ProductType.PACKAGED) return mrp
        val safeGrams = weightGrams.coerceAtLeast(250)
        val perKg = if (mrpPerKg > 0) mrpPerKg else mrp
        return ((perKg * safeGrams) / 1000.0).roundToInt()
    }
}

data class CartItem(
    val cartItemKey: String, // "${product.id}_${selectedWeightGrams}"
    val product: Product,
    val quantity: Int = 1,
    val selectedWeightGrams: Int = 1000 // Used when product.productType == ProductType.LOOSE
) {
    val unitSellingPrice: Int
        get() = product.calculatePriceForWeight(selectedWeightGrams)

    val unitMrp: Int
        get() = product.calculateMrpForWeight(selectedWeightGrams)

    val lineSubtotal: Int
        get() = unitSellingPrice * quantity

    val lineMrpTotal: Int
        get() = unitMrp * quantity

    val lineDiscount: Int
        get() = (lineMrpTotal - lineSubtotal).coerceAtLeast(0)

    val selectedUnitLabel: String
        get() = if (product.productType == ProductType.LOOSE) {
            LooseWeightOption.fromGrams(selectedWeightGrams).displayLabel
        } else {
            product.unit
        }
}

data class PinCodeVerificationResult(
    val pinCode: String,
    val isValidFormat: Boolean,
    val isVerified: Boolean,
    val town: String = "",
    val district: String = "",
    val state: String = "",
    val errorMessage: String? = null
)

data class DeliveryAddress(
    val id: String = "",
    val customerId: String = "",
    val label: String = "Home", // Home, Work, Other
    val addressType: String = "Home",
    val houseNumber: String = "",
    val street: String = "",
    val area: String = "",
    val locality: String = "",
    val landmark: String = "",
    val city: String = "",
    val town: String = "",
    val district: String = "",
    val state: String = "",
    val pinCode: String = "",
    val latitude: Double = 12.9716,
    val longitude: Double = 77.5946,
    val isDefault: Boolean = false
) {
    val formattedAddress: String
        get() = listOf(
            houseNumber,
            street,
            locality.ifBlank { area },
            landmark,
            town,
            district.ifBlank { city },
            state
        ).filter { it.isNotBlank() }.joinToString(", ") + " - $pinCode"
}

enum class OrderStatus(val displayTitle: String, val stepIndex: Int) {
    ORDER_PLACED("ORDER PLACED", 0),
    ORDER_ACCEPTED("ORDER ACCEPTED", 1),
    PREPARING("PREPARING YOUR ORDER", 2),
    READY_FOR_DELIVERY("READY FOR DELIVERY", 3),
    OUT_FOR_DELIVERY("OUT FOR DELIVERY", 4),
    DELIVERED("DELIVERED", 5),
    CANCELLED("CANCELLED", -1)
}

/**
 * Immutable product snapshot stored inside an OrderRecord when the order is confirmed.
 * Preserves the exact product photo, name, ordered weight/quantity, and unit price.
 */
data class OrderSnapshotItem(
    val productId: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val productType: ProductType = ProductType.PACKAGED,
    val selectedWeightGrams: Int = 1000,
    val selectedUnitLabel: String = "1 unit",
    val quantity: Int = 1,
    val unitPrice: Int = 0,
    val mrpPrice: Int = 0
) {
    val lineTotal: Int
        get() = unitPrice * quantity
}

data class OrderRecord(
    val orderId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val deliveryLat: Double = 12.9716,
    val deliveryLng: Double = 77.5946,
    val itemsSummary: String = "",
    val itemsSnapshotJson: String = "",
    val itemsSnapshot: List<OrderSnapshotItem> = emptyList(),
    val itemCount: Int = 1,
    val subtotal: Int = 0,
    val discount: Int = 0,
    val deliveryCharge: Int = 0,
    val finalTotal: Int = 0,
    val paymentMethod: String = "COD",
    val paymentStatus: String = "PENDING",
    val orderStatus: OrderStatus = OrderStatus.ORDER_PLACED,
    val deliveryInstructions: String = "",
    val deliverySlot: String = "Instant Delivery (15-20 mins)",
    val estimatedDeliveryText: String = "",
    val createdAtIso: String = "",
    val updatedAtIso: String = ""
)

data class StoreSettings(
    val isOpen: Boolean = true,
    val closedMessage: String = "JIPTO is currently closed for restocking fresh harvest.",
    val nextOpeningTime: String = "",
    val allowBrowseWhenClosed: Boolean = true
)

data class DeliverySettings(
    val storeLat: Double = 12.9716,
    val storeLng: Double = 77.5946,
    val maxRadiusKm: Double = 8.0,
    val baseDeliveryCharge: Int = 25,
    val freeDeliveryThreshold: Int = 299,
    val minimumOrderAmount: Int = 99
)

data class DeliveryTrackingInfo(
    val orderId: String = "",
    val customerId: String = "",
    val deliveryPartnerId: String = "",
    val partnerName: String = "",
    val partnerPhone: String = "",
    val hasLiveGps: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdatedAtIso: String = ""
)

data class ApkReleaseMetadata(
    val isAvailable: Boolean = false,
    val versionName: String = "1.0.0",
    val apkFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "—",
    val buildDateFormatted: String = "—",
    val downloadUrl: String = "",
    val releasePageUrl: String = "",
    val repositorySlug: String = "",
    val statusMessage: String = "APK is not available yet."
)

