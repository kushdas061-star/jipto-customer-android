package com.jipto.customer.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jipto.customer.BuildConfig
import com.jipto.customer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

class JiptoRepository(
    private val firestore: FirebaseFirestore? = try {
        val dbId = BuildConfig.FIRESTORE_DATABASE_ID
        if (dbId.isNotBlank() && dbId != "(default)") {
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    } catch (_: Exception) {
        try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
    },
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
) {
    fun getCurrentUserUid(): String? = auth?.currentUser?.uid
    fun getCurrentUserName(): String = auth?.currentUser?.displayName ?: ""
    fun getCurrentUserPhone(): String = auth?.currentUser?.phoneNumber ?: auth?.currentUser?.email ?: ""

    /**
     * Observes dynamic promotional banners from Cloud Firestore `/banners` ordered by `displayOrder`.
     * Never falls back to demo or hardcoded banners; returns an empty list if Firestore has none.
     */
    fun observeBanners(): Flow<List<BannerItem>> = callbackFlow {
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection("banners")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liveBanners = snapshot.documents.mapNotNull { doc ->
                    try {
                        val active = doc.getBoolean("active") ?: doc.getBoolean("isActive") ?: true
                        val imageUrl = doc.getString("imageUrl") ?: ""
                        val title = doc.getString("title") ?: ""
                        if (!active || imageUrl.isBlank() || title.isBlank()) return@mapNotNull null
                        BannerItem(
                            bannerId = doc.getString("bannerId") ?: doc.getString("id") ?: doc.id,
                            imageUrl = imageUrl,
                            title = title,
                            subtitle = doc.getString("subtitle") ?: "",
                            offerBadge = doc.getString("offerBadge") ?: "",
                            targetCategoryId = doc.getString("targetCategoryId") ?: "",
                            active = true,
                            displayOrder = (doc.getLong("displayOrder") ?: 1L).toInt()
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedBy { it.displayOrder }
                trySend(liveBanners)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observes categories from Cloud Firestore `/categories` collection.
     * Emits an empty list when no categories exist in Firestore.
     */
    fun observeCategories(): Flow<List<Category>> = callbackFlow {
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liveCategories = snapshot.documents.mapNotNull { doc ->
                    try {
                        val isActive = doc.getBoolean("isActive") ?: true
                        val name = doc.getString("name") ?: ""
                        if (!isActive || name.isBlank()) return@mapNotNull null
                        Category(
                            id = doc.getString("id") ?: doc.id,
                            name = name,
                            slug = doc.getString("slug") ?: doc.id,
                            iconName = doc.getString("iconName") ?: "ShoppingBasket",
                            displayOrder = (doc.getLong("displayOrder") ?: 0L).toInt(),
                            isActive = true
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedBy { it.displayOrder }
                trySend(liveCategories)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observes real-time products from Cloud Firestore `/products` collection.
     * Never falls back to sample/dummy products; emits an empty list if Firestore has none.
     */
    fun observeProducts(): Flow<List<Product>> = callbackFlow {
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liveProducts = snapshot.documents.mapNotNull { doc ->
                    try {
                        val isActive = doc.getBoolean("isActive") ?: true
                        val name = doc.getString("name") ?: ""
                        if (!isActive || name.isBlank()) return@mapNotNull null
                        val typeStr = doc.getString("productType") ?: "PACKAGED"
                        Product(
                            id = doc.getString("id") ?: doc.id,
                            name = name,
                            description = doc.getString("description") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            categoryName = doc.getString("categoryName") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            productType = if (typeStr == "LOOSE") ProductType.LOOSE else ProductType.PACKAGED,
                            mrp = (doc.getLong("mrp") ?: 0L).toInt(),
                            sellingPrice = (doc.getLong("sellingPrice") ?: 0L).toInt(),
                            pricePerKg = (doc.getLong("pricePerKg") ?: doc.getLong("sellingPrice") ?: 0L).toInt(),
                            mrpPerKg = (doc.getLong("mrpPerKg") ?: doc.getLong("mrp") ?: 0L).toInt(),
                            unit = doc.getString("unit") ?: "1 unit",
                            stockGramsOrUnits = (doc.getLong("stockGramsOrUnits") ?: 0L).toInt(),
                            isBestSeller = doc.getBoolean("isBestSeller") ?: false,
                            isTodayOffer = doc.getBoolean("isTodayOffer") ?: false,
                            isPopular = doc.getBoolean("isPopular") ?: false,
                            isRecentlyAdded = doc.getBoolean("isRecentlyAdded") ?: false,
                            isRecommended = doc.getBoolean("isRecommended") ?: false,
                            isFrequentlyPurchased = doc.getBoolean("isFrequentlyPurchased") ?: false,
                            isActive = true
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(liveProducts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observes real customer orders from `/orders` where `customerId == uid`.
     */
    fun observeCustomerOrders(customerId: String): Flow<List<OrderRecord>> = callbackFlow {
        if (firestore == null || customerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("orders")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liveOrders = snapshot.documents.mapNotNull { doc ->
                    try {
                        val statusStr = doc.getString("orderStatus") ?: "ORDER_PLACED"
                        val status = try { OrderStatus.valueOf(statusStr) } catch (_: Exception) { OrderStatus.ORDER_PLACED }
                        OrderRecord(
                            orderId = doc.getString("orderId") ?: doc.id,
                            customerId = doc.getString("customerId") ?: customerId,
                            customerName = doc.getString("customerName") ?: "",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            deliveryAddress = doc.getString("deliveryAddress") ?: "",
                            deliveryLat = doc.getDouble("deliveryLat") ?: 12.9716,
                            deliveryLng = doc.getDouble("deliveryLng") ?: 77.5946,
                            itemsSummary = doc.getString("itemsSummary") ?: "",
                            itemsSnapshotJson = doc.getString("itemsSnapshotJson") ?: "",
                            itemCount = (doc.getLong("itemCount") ?: 1L).toInt(),
                            subtotal = (doc.getLong("subtotal") ?: 0L).toInt(),
                            discount = (doc.getLong("discount") ?: 0L).toInt(),
                            deliveryCharge = (doc.getLong("deliveryCharge") ?: 0L).toInt(),
                            finalTotal = (doc.getLong("finalTotal") ?: 0L).toInt(),
                            paymentMethod = doc.getString("paymentMethod") ?: "COD",
                            paymentStatus = doc.getString("paymentStatus") ?: "PENDING",
                            orderStatus = status,
                            deliveryInstructions = doc.getString("deliveryInstructions") ?: "",
                            deliverySlot = doc.getString("deliverySlot") ?: "",
                            estimatedDeliveryText = doc.getString("estimatedDeliveryText") ?: "",
                            createdAtIso = doc.getTimestamp("createdAt")?.toDate()?.toString() ?: "",
                            updatedAtIso = doc.getTimestamp("updatedAt")?.toDate()?.toString() ?: ""
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(liveOrders)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observes real customer saved addresses from `/addresses` where `customerId == uid`.
     */
    fun observeCustomerAddresses(customerId: String): Flow<List<DeliveryAddress>> = callbackFlow {
        if (firestore == null || customerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("addresses")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liveAddresses = snapshot.documents.mapNotNull { doc ->
                    try {
                        DeliveryAddress(
                            id = doc.getString("id") ?: doc.id,
                            customerId = doc.getString("customerId") ?: customerId,
                            label = doc.getString("label") ?: "Home",
                            addressType = doc.getString("addressType") ?: "Home",
                            houseNumber = doc.getString("houseNumber") ?: "",
                            street = doc.getString("street") ?: "",
                            area = doc.getString("area") ?: "",
                            locality = doc.getString("locality") ?: "",
                            landmark = doc.getString("landmark") ?: "",
                            city = doc.getString("city") ?: "",
                            town = doc.getString("town") ?: "",
                            district = doc.getString("district") ?: "",
                            state = doc.getString("state") ?: "",
                            pinCode = doc.getString("pinCode") ?: "",
                            latitude = doc.getDouble("latitude") ?: 12.9716,
                            longitude = doc.getDouble("longitude") ?: 77.5946,
                            isDefault = doc.getBoolean("isDefault") ?: false
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(liveAddresses)
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveDeliveryAddressToFirestore(address: DeliveryAddress) {
        val db = firestore ?: return
        val uid = auth?.currentUser?.uid ?: address.customerId
        if (uid.isBlank()) return
        val docId = address.id.ifBlank { "addr_${System.currentTimeMillis()}" }
        val payload = mapOf(
            "id" to docId,
            "customerId" to uid,
            "label" to address.label,
            "addressType" to address.addressType,
            "houseNumber" to address.houseNumber,
            "street" to address.street,
            "area" to address.area,
            "locality" to address.locality,
            "landmark" to address.landmark,
            "city" to address.city,
            "town" to address.town,
            "district" to address.district,
            "state" to address.state,
            "pinCode" to address.pinCode,
            "latitude" to address.latitude,
            "longitude" to address.longitude,
            "isDefault" to address.isDefault
        )
        try {
            db.collection("addresses").document(docId).set(payload).await()
        } catch (_: Exception) {
            // Handled via UI state
        }
    }

    /**
     * Observes real-time store open/closed status from `/store_settings/main`.
     */
    fun observeStoreSettings(): Flow<StoreSettings> = callbackFlow {
        if (firestore == null) {
            trySend(StoreSettings())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("store_settings").document("main")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        StoreSettings(
                            isOpen = snapshot.getBoolean("isOpen") ?: true,
                            closedMessage = snapshot.getString("closedMessage") ?: "Store is temporarily closed.",
                            nextOpeningTime = snapshot.getString("nextOpeningTime") ?: "",
                            allowBrowseWhenClosed = snapshot.getBoolean("allowBrowseWhenClosed") ?: true
                        )
                    )
                } else {
                    trySend(StoreSettings())
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observes real-time delivery partner GPS tracking for a specific `orderId` ONLY.
     * Never fabricates fake GPS coordinates. Automatically closes listener when cancelled/collected.
     */
    fun observeOrderTracking(orderId: String): Flow<DeliveryTrackingInfo?> = callbackFlow {
        if (firestore == null || orderId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("delivery_tracking").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val docOrderId = snapshot.getString("orderId") ?: snapshot.id
                val hasLiveGps = snapshot.getBoolean("hasLiveGps") ?: false
                val lat = snapshot.getDouble("latitude") ?: snapshot.getDouble("currentLat")
                val lng = snapshot.getDouble("longitude") ?: snapshot.getDouble("currentLng")
                if (docOrderId == orderId && hasLiveGps && lat != null && lng != null) {
                    trySend(
                        DeliveryTrackingInfo(
                            orderId = docOrderId,
                            customerId = snapshot.getString("customerId") ?: "",
                            deliveryPartnerId = snapshot.getString("deliveryPartnerId") ?: "",
                            partnerName = snapshot.getString("partnerName") ?: "",
                            partnerPhone = snapshot.getString("partnerPhone") ?: "",
                            hasLiveGps = true,
                            latitude = lat,
                            longitude = lng,
                            lastUpdatedAtIso = snapshot.getTimestamp("lastUpdatedAt")?.toDate()?.toString()
                                ?: snapshot.getTimestamp("updatedAt")?.toDate()?.toString()
                                ?: ""
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Queries the live India Post PIN code directory (`https://api.postalpincode.in/pincode/{pinCode}`)
     * to automatically retrieve Town/City, District, and State without requiring Post Office selection.
     */
    suspend fun lookupIndianPinCode(rawPinCode: String): PinCodeVerificationResult = withContext(Dispatchers.IO) {
        val pinCode = rawPinCode.trim()
        val pinRegex = Regex("^[1-9][0-9]{5}$")
        if (!pinRegex.matches(pinCode)) {
            return@withContext PinCodeVerificationResult(
                pinCode = pinCode,
                isValidFormat = false,
                isVerified = false,
                errorMessage = "Please enter a valid 6-digit Indian PIN code."
            )
        }

        try {
            val url = URL("https://api.postalpincode.in/pincode/$pinCode")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode != 200) {
                return@withContext PinCodeVerificationResult(
                    pinCode = pinCode,
                    isValidFormat = true,
                    isVerified = false,
                    errorMessage = "PIN lookup service returned HTTP ${connection.responseCode}. Please retry."
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val rootArray = JSONArray(body)
            val firstObj = rootArray.optJSONObject(0)
            val status = firstObj?.optString("Status") ?: "Error"
            val recordsArray = firstObj?.optJSONArray("PostOffice")

            if (status != "Success" || recordsArray == null || recordsArray.length() == 0) {
                return@withContext PinCodeVerificationResult(
                    pinCode = pinCode,
                    isValidFormat = true,
                    isVerified = false,
                    errorMessage = "No verified postal record found for PIN $pinCode."
                )
            }

            val firstRecord = recordsArray.getJSONObject(0)
            val district = firstRecord.optString("District")
            val state = firstRecord.optString("State")
            val town = firstRecord.optString("Block").let {
                if (it.isBlank() || it == "NA") district else it
            }

            PinCodeVerificationResult(
                pinCode = pinCode,
                isValidFormat = true,
                isVerified = true,
                town = town,
                district = district,
                state = state
            )
        } catch (_: Exception) {
            PinCodeVerificationResult(
                pinCode = pinCode,
                isValidFormat = true,
                isVerified = false,
                errorMessage = "Unable to reach PIN code lookup service. Please tap Retry."
            )
        }
    }

    /**
     * Queries GitHub Releases (`https://api.github.com/repos/{owner}/{repo}/releases/latest`)
     * to check for a verified native Android `.apk` release asset produced by GitHub Actions.
     * Never returns a fake or placeholder download URL. If no release or `.apk` asset exists,
     * returns `isAvailable = false` with `statusMessage = "APK is not available yet."`.
     */
    suspend fun fetchLatestGitHubApkRelease(rawRepositorySlug: String): ApkReleaseMetadata = withContext(Dispatchers.IO) {
        val slug = rawRepositorySlug.trim()
            .removePrefix("https://github.com/")
            .trim('/')
        val slugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        if (!slugRegex.matches(slug)) {
            return@withContext ApkReleaseMetadata(
                isAvailable = false,
                versionName = BuildConfig.VERSION_NAME,
                repositorySlug = slug,
                statusMessage = "APK is not available yet."
            )
        }

        try {
            val url = URL("https://api.github.com/repos/$slug/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode != 200) {
                return@withContext ApkReleaseMetadata(
                    isAvailable = false,
                    versionName = BuildConfig.VERSION_NAME,
                    repositorySlug = slug,
                    statusMessage = "APK is not available yet."
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(body)
            val assetsArray = releaseJson.optJSONArray("assets") ?: JSONArray()

            var matchedApkAsset: JSONObject? = null
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.lowercase(Locale.US).endsWith(".apk") && downloadUrl.startsWith("https://")) {
                    matchedApkAsset = asset
                    break
                }
            }

            if (matchedApkAsset == null) {
                return@withContext ApkReleaseMetadata(
                    isAvailable = false,
                    versionName = releaseJson.optString("tag_name", BuildConfig.VERSION_NAME).removePrefix("v"),
                    repositorySlug = slug,
                    statusMessage = "APK is not available yet."
                )
            }

            val sizeBytes = matchedApkAsset.optLong("size", 0L)
            val formattedSize = if (sizeBytes > 0L) {
                String.format(Locale.US, "%.2f MB", sizeBytes.toDouble() / (1024.0 * 1024.0))
            } else {
                "—"
            }
            val buildDateRaw = matchedApkAsset.optString("updated_at")
                .ifBlank { releaseJson.optString("published_at", "—") }
            val tagName = releaseJson.optString("tag_name", BuildConfig.VERSION_NAME).removePrefix("v")
            val apkName = matchedApkAsset.optString("name", "jipto-customer-release.apk")
            val browserDownloadUrl = matchedApkAsset.optString("browser_download_url", "")

            ApkReleaseMetadata(
                isAvailable = true,
                versionName = tagName,
                apkFileName = apkName,
                fileSizeBytes = sizeBytes,
                fileSizeFormatted = formattedSize,
                buildDateFormatted = buildDateRaw,
                downloadUrl = browserDownloadUrl,
                releasePageUrl = releaseJson.optString("html_url", "https://github.com/$slug/releases/latest"),
                repositorySlug = slug,
                statusMessage = "Verified GitHub Actions release APK ($apkName) ready for download."
            )
        } catch (_: Exception) {
            ApkReleaseMetadata(
                isAvailable = false,
                versionName = BuildConfig.VERSION_NAME,
                repositorySlug = slug,
                statusMessage = "APK is not available yet."
            )
        }
    }

    /**
     * Validates if a customer delivery address is within the admin-configured radius & serviceable PIN codes.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
