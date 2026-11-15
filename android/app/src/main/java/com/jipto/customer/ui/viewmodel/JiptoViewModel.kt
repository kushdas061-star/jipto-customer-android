package com.jipto.customer.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jipto.customer.BuildConfig
import com.jipto.customer.data.model.*
import com.jipto.customer.data.repository.JiptoRepository
import com.jipto.customer.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption(val labelEn: String, val labelHi: String) {
    POPULARITY("Popularity", "लोकप्रियता"),
    PRICE_LOW_TO_HIGH("Price: Low to High", "कीमत: कम से ज़्यादा"),
    PRICE_HIGH_TO_LOW("Price: High to Low", "कीमत: ज़्यादा से कम")
}

enum class AppLanguageCode(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)")
}

data class JiptoUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguageCode = AppLanguageCode.ENGLISH,
    val isSettingsOpen: Boolean = false,
    val storeSettings: StoreSettings = StoreSettings(),
    val deliverySettings: DeliverySettings = DeliverySettings(),
    val banners: List<BannerItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val recentSearches: List<String> = emptyList(),
    val sortOption: SortOption = SortOption.POPULARITY,
    val maxPriceFilter: Int = 1000,
    val cartItems: List<CartItem> = emptyList(),
    val savedAddresses: List<DeliveryAddress> = emptyList(),
    val selectedAddress: DeliveryAddress? = null,
    val pinLookupResult: PinCodeVerificationResult? = null,
    val isPinLookupLoading: Boolean = false,
    val orders: List<OrderRecord> = emptyList(),
    val selectedOrderForDetails: OrderRecord? = null,
    val selectedOrderForTracking: OrderRecord? = null,
    val liveTrackingInfo: DeliveryTrackingInfo? = null,
    val snackbarMessage: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val isLoggedIn: Boolean = false,
    val githubRepoSlug: String = "",
    val apkReleaseMetadata: ApkReleaseMetadata = ApkReleaseMetadata(),
    val isCheckingApk: Boolean = false,
    val apkDownloadStatus: String = "APK is not available yet."
)

class JiptoViewModel(
    private val repository: JiptoRepository = JiptoRepository(),
    private val prefs: SharedPreferences? = null
) : ViewModel() {

    private var trackingJob: Job? = null

    private val _uiState = MutableStateFlow(
        JiptoUiState(
            themeMode = ThemeMode.valueOf(
                prefs?.getString("jipto_theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            ),
            language = AppLanguageCode.valueOf(
                prefs?.getString("jipto_app_lang", AppLanguageCode.ENGLISH.name) ?: AppLanguageCode.ENGLISH.name
            ),
            githubRepoSlug = prefs?.getString("jipto_github_repo", BuildConfig.GITHUB_REPO_SLUG)
                ?: BuildConfig.GITHUB_REPO_SLUG,
            customerName = repository.getCurrentUserName(),
            customerPhone = repository.getCurrentUserPhone(),
            isLoggedIn = repository.getCurrentUserUid() != null
        )
    )
    val uiState: StateFlow<JiptoUiState> = _uiState.asStateFlow()

    init {
        observeLiveStoreData()
        checkLatestApkRelease(_uiState.value.githubRepoSlug)
    }

    private fun observeLiveStoreData() {
        viewModelScope.launch {
            repository.observeStoreSettings().collect { settings ->
                _uiState.update { it.copy(storeSettings = settings) }
            }
        }
        viewModelScope.launch {
            repository.observeBanners().collect { liveBanners ->
                _uiState.update { it.copy(banners = liveBanners) }
            }
        }
        viewModelScope.launch {
            repository.observeCategories().collect { liveCategories ->
                _uiState.update { it.copy(categories = liveCategories) }
            }
        }
        viewModelScope.launch {
            repository.observeProducts().collect { liveProducts ->
                _uiState.update { it.copy(products = liveProducts) }
            }
        }
        val uid = repository.getCurrentUserUid()
        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                repository.observeCustomerOrders(uid).collect { liveOrders ->
                    _uiState.update { it.copy(orders = liveOrders) }
                }
            }
            viewModelScope.launch {
                repository.observeCustomerAddresses(uid).collect { liveAddresses ->
                    _uiState.update { state ->
                        val selected = state.selectedAddress
                            ?: liveAddresses.find { it.isDefault }
                            ?: liveAddresses.firstOrNull()
                        state.copy(savedAddresses = liveAddresses, selectedAddress = selected)
                    }
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs?.edit()?.putString("jipto_theme_mode", mode.name)?.apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(lang: AppLanguageCode) {
        prefs?.edit()?.putString("jipto_app_lang", lang.name)?.apply()
        _uiState.update { it.copy(language = lang) }
    }

    fun toggleSettings(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    fun updateGithubRepoSlug(slug: String) {
        val cleaned = slug.trim()
        prefs?.edit()?.putString("jipto_github_repo", cleaned)?.apply()
        _uiState.update { it.copy(githubRepoSlug = cleaned) }
    }

    fun checkLatestApkRelease(repoSlug: String = _uiState.value.githubRepoSlug) {
        val cleaned = repoSlug.trim()
        prefs?.edit()?.putString("jipto_github_repo", cleaned)?.apply()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    githubRepoSlug = cleaned,
                    isCheckingApk = true,
                    apkDownloadStatus = "Checking GitHub Releases for latest signed JIPTO APK..."
                )
            }
            val metadata = repository.fetchLatestGitHubApkRelease(cleaned)
            _uiState.update {
                it.copy(
                    isCheckingApk = false,
                    apkReleaseMetadata = metadata,
                    apkDownloadStatus = metadata.statusMessage
                )
            }
        }
    }

    fun updateApkDownloadStatus(statusMessage: String) {
        _uiState.update { it.copy(apkDownloadStatus = statusMessage) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun openOrderDetails(order: OrderRecord?) {
        stopLiveOrderTracking()
        _uiState.update { it.copy(selectedOrderForDetails = order, selectedOrderForTracking = null) }
    }

    fun openOrderTracking(order: OrderRecord?) {
        stopLiveOrderTracking()
        _uiState.update { it.copy(selectedOrderForTracking = order, selectedOrderForDetails = null) }
        if (order != null && order.orderStatus != OrderStatus.DELIVERED && order.orderStatus != OrderStatus.CANCELLED) {
            trackingJob = viewModelScope.launch {
                repository.observeOrderTracking(order.orderId).collect { info ->
                    _uiState.update { it.copy(liveTrackingInfo = info) }
                }
            }
        }
    }

    fun stopLiveOrderTracking() {
        trackingJob?.cancel()
        trackingJob = null
        _uiState.update { it.copy(liveTrackingInfo = null) }
    }

    fun verifyPinCode(pinCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPinLookupLoading = true) }
            val result = repository.lookupIndianPinCode(pinCode)
            _uiState.update { it.copy(isPinLookupLoading = false, pinLookupResult = result) }
        }
    }

    fun saveOrUpdateAddress(address: DeliveryAddress) {
        viewModelScope.launch {
            repository.saveDeliveryAddressToFirestore(address)
        }
        _uiState.update { state ->
            val exists = state.savedAddresses.any { it.id == address.id }
            val updatedList = if (exists) {
                state.savedAddresses.map { if (it.id == address.id) address else it }
            } else {
                state.savedAddresses + address
            }
            state.copy(
                savedAddresses = updatedList,
                selectedAddress = address,
                snackbarMessage = "Delivery address saved."
            )
        }
    }

    fun selectAddress(address: DeliveryAddress) {
        _uiState.update { it.copy(selectedAddress = address) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearchHistory() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun updateSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun addToCart(product: Product, weightGrams: Int = 1000, quantityToAdd: Int = 1) {
        val normalizedWeight = if (product.productType == ProductType.LOOSE) weightGrams.coerceAtLeast(250) else 1000
        val safeQty = quantityToAdd.coerceAtLeast(1)
        val key = "${product.id}_$normalizedWeight"

        _uiState.update { state ->
            val existing = state.cartItems.find { it.cartItemKey == key }
            val nextQty = (existing?.quantity ?: 0) + safeQty
            val requiredStock = if (product.productType == ProductType.LOOSE) nextQty * normalizedWeight else nextQty
            if (requiredStock > product.stockGramsOrUnits) {
                return@update state.copy(
                    snackbarMessage = "Cannot add more than available stock for ${product.name}."
                )
            }
            val updatedCart = if (existing != null) {
                state.cartItems.map {
                    if (it.cartItemKey == key) it.copy(quantity = nextQty) else it
                }
            } else {
                state.cartItems + CartItem(
                    cartItemKey = key,
                    product = product,
                    quantity = safeQty,
                    selectedWeightGrams = normalizedWeight
                )
            }
            state.copy(
                cartItems = updatedCart,
                snackbarMessage = "Added ${product.name} (×$safeQty) to Cart"
            )
        }
    }

    fun updateCartQuantity(cartItemKey: String, delta: Int) {
        _uiState.update { state ->
            val updated = state.cartItems.mapNotNull { item ->
                if (item.cartItemKey != cartItemKey) return@mapNotNull item
                val nextQty = item.quantity + delta
                if (nextQty <= 0) return@mapNotNull null
                val requiredStock = if (item.product.productType == ProductType.LOOSE) {
                    nextQty * item.selectedWeightGrams
                } else {
                    nextQty
                }
                if (requiredStock > item.product.stockGramsOrUnits) item else item.copy(quantity = nextQty)
            }
            state.copy(cartItems = updated)
        }
    }

    /**
     * Permitted customer cancellation only while order is in ORDER_PLACED stage.
     * Confirmed order items, weights, prices, totals, and delivery address are permanently immutable.
     */
    fun cancelOrder(orderId: String) {
        _uiState.update { state ->
            val updatedOrders = state.orders.map { ord ->
                if (ord.orderId == orderId && ord.orderStatus == OrderStatus.ORDER_PLACED) {
                    ord.copy(orderStatus = OrderStatus.CANCELLED)
                } else {
                    ord
                }
            }
            val updatedDetails = state.selectedOrderForDetails?.let {
                if (it.orderId == orderId && it.orderStatus == OrderStatus.ORDER_PLACED) {
                    it.copy(orderStatus = OrderStatus.CANCELLED)
                } else it
            }
            state.copy(orders = updatedOrders, selectedOrderForDetails = updatedDetails)
        }
    }
}
