package com.jipto.customer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsApiSettings
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.maps.android.compose.*
import com.jipto.customer.data.model.*
import com.jipto.customer.ui.theme.*
import com.jipto.customer.ui.viewmodel.AppLanguageCode
import com.jipto.customer.ui.viewmodel.JiptoViewModel
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            try {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(this, options)
            } catch (_: Exception) {
            }
        }

        MapsApiSettings.addInternalUsageAttributionId(this, "gmp_mcp_codeassist_v1_aistudio")
        val prefs = getSharedPreferences("jipto_user_prefs", Context.MODE_PRIVATE)
        val viewModel = JiptoViewModel(prefs = prefs)

        setContent {
            val state by viewModel.uiState.collectAsState()
            JiptoTheme(themeMode = state.themeMode) {
                JiptoCustomerApp(viewModel = viewModel)
            }
        }
    }
}

/**
 * Mandatory 5 Bottom Navigation Tabs:
 * 1. Home
 * 2. Search
 * 3. Cart
 * 4. My Orders
 * 5. Profile
 */
enum class BottomNavTab(val labelEn: String, val labelHi: String) {
    HOME("Home", "होम"),
    SEARCH("Search", "खोजें"),
    CART("Cart", "कार्ट"),
    MY_ORDERS("My Orders", "मेरे ऑर्डर"),
    PROFILE("Profile", "प्रोफ़ाइल")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JiptoCustomerApp(viewModel: JiptoViewModel) {
    val state by viewModel.uiState.collectAsState()
    var currentTab by remember { mutableStateOf(BottomNavTab.HOME) }
    val isHindi = state.language == AppLanguageCode.HINDI
    val totalCartItems = state.cartItems.sumOf { it.quantity }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Row: Brand Title + Top-Right Settings Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JIPTO",
                            style = MaterialTheme.typography.titleLarge,
                            color = JiptoGreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleSettings(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = if (isHindi) "सेटिंग्स" else "Settings"
                                )
                            }
                        }
                    }

                    // Stacked Non-Overlapping Delivery Address Block:
                    // 1. Delivery location heading at top
                    // 2. Selected delivery address below it (no fake fallback address)
                    // 3. Change Delivery Address button below the address
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isHindi) "डिलीवरी का पता" else "DELIVERY LOCATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = JiptoGreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.selectedAddress?.formattedAddress
                                ?: if (isHindi)
                                    "कोई डिलीवरी पता सहेजा नहीं गया है। अपना पता जोड़ने के लिए नीचे टैप करें।"
                                else
                                    "No delivery address saved yet. Tap below to add your delivery address.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.toggleSettings(false)
                                currentTab = BottomNavTab.PROFILE
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "डिलीवरी का पता बदलें" else "Change Delivery Address",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                BottomNavTab.entries.forEach { tab ->
                    val label = if (isHindi) tab.labelHi else tab.labelEn
                    NavigationBarItem(
                        selected = currentTab == tab && !state.isSettingsOpen,
                        onClick = {
                            viewModel.toggleSettings(false)
                            viewModel.openOrderDetails(null)
                            viewModel.openOrderTracking(null)
                            currentTab = tab
                        },
                        icon = {
                            val icon = when (tab) {
                                BottomNavTab.HOME -> Icons.Default.Home
                                BottomNavTab.SEARCH -> Icons.Default.Search
                                BottomNavTab.CART -> Icons.Default.ShoppingCart
                                BottomNavTab.MY_ORDERS -> Icons.Default.ReceiptLong
                                BottomNavTab.PROFILE -> Icons.Default.Person
                            }
                            if (tab == BottomNavTab.CART && totalCartItems > 0) {
                                BadgedBox(badge = { Badge { Text("$totalCartItems") } }) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (state.isSettingsOpen) {
                JiptoSettingsScreen(
                    themeMode = state.themeMode,
                    language = state.language,
                    githubRepoSlug = state.githubRepoSlug,
                    apkMetadata = state.apkReleaseMetadata,
                    isCheckingApk = state.isCheckingApk,
                    apkDownloadStatus = state.apkDownloadStatus,
                    onSelectTheme = { viewModel.setThemeMode(it) },
                    onSelectLanguage = { viewModel.setLanguage(it) },
                    onUpdateRepoSlug = { viewModel.updateGithubRepoSlug(it) },
                    onCheckApkRelease = { viewModel.checkLatestApkRelease(it) },
                    onUpdateDownloadStatus = { viewModel.updateApkDownloadStatus(it) },
                    onClose = { viewModel.toggleSettings(false) }
                )
            } else {
                when (currentTab) {
                    BottomNavTab.HOME -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Promotional Banner Carousel (Full Span; Hidden if no active banners in Firebase)
                            if (state.banners.isNotEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    JiptoPromotionalBannerCarousel(banners = state.banners)
                                }
                            }

                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    text = if (isHindi) "ताज़ा किराना उत्पाद" else "Store Catalog",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            if (state.products.isEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = JiptoGreenPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = if (isHindi)
                                                    "अभी कोई उत्पाद उपलब्ध नहीं है"
                                                else
                                                    "No products available yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isHindi)
                                                    "इस स्टोर में उत्पाद जोड़े जाने पर वे यहाँ तुरंत दिखाई देंगे।"
                                                else
                                                    "Live products from the Firebase store catalog will appear here automatically once added.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.products, key = { it.id }) { product ->
                                    JiptoCompactProductCard(
                                        product = product,
                                        isHindi = isHindi,
                                        onClick = {},
                                        onAddToCart = { weight, qty -> viewModel.addToCart(product, weight, qty) }
                                    )
                                }
                            }
                        }
                    }
                    BottomNavTab.SEARCH -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    text = if (isHindi) "किराना खोजें, फ़िल्टर और सॉर्ट करें" else "Search, Filter & Sort Groceries",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            if (state.categories.isNotEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(state.categories) { cat ->
                                            FilterChip(
                                                selected = state.selectedCategoryId == cat.id,
                                                onClick = { viewModel.selectCategory(cat.id) },
                                                label = { Text(cat.name) }
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.products.isEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "कोई उत्पाद नहीं मिला" else "No store products found",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isHindi)
                                                    "Firebase डेटाबेस में उत्पाद प्रकाशित होने पर वे यहाँ दिखाई देंगे।"
                                                else
                                                    "Products published in Firebase Firestore will appear here.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.products, key = { it.id }) { product ->
                                    JiptoCompactProductCard(
                                        product = product,
                                        isHindi = isHindi,
                                        onClick = {},
                                        onAddToCart = { weight, qty -> viewModel.addToCart(product, weight, qty) }
                                    )
                                }
                            }
                        }
                    }
                    BottomNavTab.CART -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = if (isHindi) "शॉपिंग कार्ट" else "Shopping Cart",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            if (state.cartItems.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "आपका कार्ट खाली है" else "Your cart is empty",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isHindi)
                                                    "खरीदारी शुरू करने के लिए होम स्क्रीन से उत्पाद जोड़ें।"
                                                else
                                                    "Add fresh groceries from the Home catalog to begin checkout.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.cartItems, key = { it.cartItemKey }) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(item.product.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "${item.selectedUnitLabel} × ${item.quantity}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Text("₹${item.lineSubtotal}", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BottomNavTab.MY_ORDERS -> {
                        val trackingOrder = state.selectedOrderForTracking
                        val detailsOrder = state.selectedOrderForDetails
                        when {
                            trackingOrder != null -> {
                                JiptoLiveOrderTrackingScreen(
                                    order = trackingOrder,
                                    trackingInfo = state.liveTrackingInfo,
                                    isHindi = isHindi,
                                    onBack = { viewModel.openOrderTracking(null) }
                                )
                            }
                            detailsOrder != null -> {
                                JiptoOrderDetailsScreen(
                                    order = detailsOrder,
                                    isHindi = isHindi,
                                    onBack = { viewModel.openOrderDetails(null) },
                                    onTrackOrder = { viewModel.openOrderTracking(detailsOrder) },
                                    onCancelOrder = { viewModel.cancelOrder(detailsOrder.orderId) }
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = if (isHindi) "मेरे ऑर्डर" else "My Orders",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                    if (state.orders.isEmpty()) {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(24.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isHindi) "अभी तक कोई ऑर्डर नहीं है" else "No orders placed yet",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (isHindi)
                                                            "आपके वास्तविक ऑर्डर यहाँ दिखाई देंगे।"
                                                        else
                                                            "Your real confirmed orders from Firebase will appear here.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(state.orders, key = { it.orderId }) { order ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.openOrderDetails(order) },
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "Order #${order.orderId} · ${order.orderStatus.displayTitle}",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Total: ₹${order.finalTotal} (${order.paymentMethod} · ${order.paymentStatus})",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BottomNavTab.PROFILE -> {
                        var editingAddress by remember { mutableStateOf<DeliveryAddress?>(null) }
                        var showAddressForm by remember { mutableStateOf(false) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isHindi) "ग्राहक प्रोफ़ाइल और पते" else "Customer Profile & Addresses",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Button(
                                        onClick = {
                                            editingAddress = null
                                            showAddressForm = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary)
                                    ) {
                                        Text(if (isHindi) "+ नया पता" else "+ Add Address")
                                    }
                                }
                            }

                            if (showAddressForm) {
                                item {
                                    JiptoAddEditAddressCard(
                                        initialAddress = editingAddress,
                                        pinLookupResult = state.pinLookupResult,
                                        isPinLoading = state.isPinLookupLoading,
                                        isHindi = isHindi,
                                        onPinChanged = { pin ->
                                            if (pin.length == 6) {
                                                viewModel.verifyPinCode(pin)
                                            }
                                        },
                                        onSave = { saved ->
                                            viewModel.saveOrUpdateAddress(saved)
                                            showAddressForm = false
                                            editingAddress = null
                                        },
                                        onCancel = {
                                            showAddressForm = false
                                            editingAddress = null
                                        }
                                    )
                                }
                            }

                            if (state.savedAddresses.isEmpty() && !showAddressForm) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "कोई पता सहेजा नहीं गया है" else "No saved delivery addresses yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isHindi)
                                                    "अपना 6-अंकीय पिन कोड दर्ज करके नया डिलीवरी पता जोड़ें।"
                                                else
                                                    "Tap '+ Add Address' above and enter your 6-digit PIN code to add a delivery address.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.savedAddresses, key = { it.id }) { addr ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${addr.label} · PIN ${addr.pinCode}",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = addr.formattedAddress,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        editingAddress = addr
                                                        showAddressForm = true
                                                    }
                                                ) {
                                                    Text(if (isHindi) "संपादित करें" else "Edit")
                                                }
                                                Button(
                                                    onClick = { viewModel.selectAddress(addr) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary)
                                                ) {
                                                    Text(if (isHindi) "डिलीवरी के लिए चुनें" else "Deliver Here")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add / Edit Delivery Address Form in Jetpack Compose:
 * - Customer enters only the 6-digit PIN code to auto-fetch Town/City, District, and State.
 * - Includes House/Flat Number, Street/Area, Locality, Landmark, and Address Type (Home/Work/Other).
 * - Completely removes any Post Office field, dropdown, or selection option.
 */
@Composable
fun JiptoAddEditAddressCard(
    initialAddress: DeliveryAddress?,
    pinLookupResult: PinCodeVerificationResult?,
    isPinLoading: Boolean,
    isHindi: Boolean,
    onPinChanged: (String) -> Unit,
    onSave: (DeliveryAddress) -> Unit,
    onCancel: () -> Unit
) {
    var pinCode by remember(initialAddress) { mutableStateOf(initialAddress?.pinCode ?: "") }
    var houseNumber by remember(initialAddress) { mutableStateOf(initialAddress?.houseNumber ?: "") }
    var street by remember(initialAddress) { mutableStateOf(initialAddress?.street ?: "") }
    var area by remember(initialAddress) { mutableStateOf(initialAddress?.area ?: "") }
    var landmark by remember(initialAddress) { mutableStateOf(initialAddress?.landmark ?: "") }
    var town by remember(initialAddress) { mutableStateOf(initialAddress?.town ?: "") }
    var district by remember(initialAddress) { mutableStateOf(initialAddress?.district ?: "") }
    var stateName by remember(initialAddress) { mutableStateOf(initialAddress?.state ?: "") }
    var label by remember(initialAddress) { mutableStateOf(initialAddress?.label ?: "Home") }

    LaunchedEffect(pinLookupResult) {
        if (pinLookupResult != null && pinLookupResult.isVerified && pinLookupResult.pinCode == pinCode) {
            town = pinLookupResult.town
            district = pinLookupResult.district
            stateName = pinLookupResult.state
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (initialAddress != null) {
                    if (isHindi) "डिलीवरी का पता संपादित करें" else "Edit Delivery Address"
                } else {
                    if (isHindi) "नया डिलीवरी पता जोड़ें" else "Add Delivery Address"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = pinCode,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() }.take(6)
                    pinCode = cleaned
                    onPinChanged(cleaned)
                },
                label = { Text(if (isHindi) "6-अंकीय पिन कोड *" else "6-Digit PIN Code *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (isPinLoading) {
                Text(
                    text = if (isHindi) "पिन कोड से शहर, ज़िला और राज्य प्राप्त किया जा रहा है..." else "Fetching Town/City, District & State from PIN...",
                    fontSize = 12.sp
                )
            }

            OutlinedTextField(
                value = houseNumber,
                onValueChange = { houseNumber = it },
                label = { Text(if (isHindi) "मकान / फ़्लैट नंबर *" else "House / Flat Number *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text(if (isHindi) "गली / सड़क / क्षेत्र *" else "Street / Area *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text(if (isHindi) "मोहल्ला / कॉलोनी" else "Locality / Colony") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = landmark,
                onValueChange = { landmark = it },
                label = { Text(if (isHindi) "लैंडमार्क (वैकल्पिक)" else "Landmark (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = town,
                onValueChange = { town = it },
                label = { Text(if (isHindi) "शहर / कस्बा" else "Town / City") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = district,
                onValueChange = { district = it },
                label = { Text(if (isHindi) "ज़िला" else "District") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = stateName,
                onValueChange = { stateName = it },
                label = { Text(if (isHindi) "राज्य" else "State") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Work", "Other").forEach { type ->
                    FilterChip(
                        selected = label == type,
                        onClick = { label = type },
                        label = { Text(type) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (pinCode.length == 6 && houseNumber.isNotBlank() && street.isNotBlank()) {
                            onSave(
                                DeliveryAddress(
                                    id = initialAddress?.id ?: "addr_${System.currentTimeMillis()}",
                                    label = label,
                                    addressType = label,
                                    houseNumber = houseNumber.trim(),
                                    street = street.trim(),
                                    area = area.trim().ifBlank { town },
                                    locality = area.trim().ifBlank { town },
                                    landmark = landmark.trim(),
                                    city = district.ifBlank { town },
                                    town = town.trim(),
                                    district = district.trim(),
                                    state = stateName.trim(),
                                    pinCode = pinCode.trim()
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary)
                ) {
                    Text(if (isHindi) "पता सहेजें" else "Save Address")
                }
            }
        }
    }
}

/**
 * Horizontal Promotional Banner Carousel loaded from Firebase `/banners`.
 * Supports automatic sliding and manual swiping. Hidden automatically when `banners` is empty.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JiptoPromotionalBannerCarousel(banners: List<BannerItem>) {
    if (banners.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { banners.size })

    LaunchedEffect(banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(4500L)
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(16.dp))
    ) { page ->
        val banner = banners[page]
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = banner.imageUrl,
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (banner.offerBadge.isNotBlank()) {
                        Text(
                            text = banner.offerBadge,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(JiptoDiscountOrange, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = banner.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (banner.subtitle.isNotBlank()) {
                        Text(
                            text = banner.subtitle,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Product Card for 2-Column Grid.
 * Displays: 1. Photo, 2. 2-Line Name, 3. Weight/Quantity, 4. MRP, 5. Selling Price (#166534 Deep Emerald),
 * 6. Discount (#F97316 Attractive Orange), 7. Decrease (-), 8. Selected Quantity, 9. Increase (+), 10. Add to Cart (#16A34A Fresh Green).
 */
@Composable
fun JiptoCompactProductCard(
    product: Product,
    isHindi: Boolean,
    onClick: () -> Unit,
    onAddToCart: (Int, Int) -> Unit
) {
    var selectedWeight by remember { mutableStateOf(LooseWeightOption.KG_1) }
    var selectedQuantity by remember { mutableIntStateOf(1) }
    val unitPrice = product.calculatePriceForWeight(selectedWeight.grams)
    val unitMrp = product.calculateMrpForWeight(selectedWeight.grams)
    val totalSelling = unitPrice * selectedQuantity
    val totalMrp = unitMrp * selectedQuantity

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 1. Product Photo + 6. Discount Badge (#F97316 Attractive Orange)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (product.discountPercentage > 0) {
                    Text(
                        text = "${product.discountPercentage}% OFF",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(6.dp)
                            .background(JiptoDiscountOrange, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. Product Name (2 Lines)
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )

            // 3. Loose Weight or Packaged Unit
            if (product.productType == ProductType.LOOSE) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(LooseWeightOption.entries) { option ->
                        FilterChip(
                            selected = selectedWeight == option,
                            onClick = { selectedWeight = option },
                            label = { Text(option.displayLabel, fontSize = 10.sp) }
                        )
                    }
                }
            } else {
                Text(
                    text = "${product.unit} · Stock: ${product.stockGramsOrUnits}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 4. MRP & 5. Selling Price (Highlighted in Deep Emerald #166534)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹$totalSelling",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (totalMrp > totalSelling) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "₹$totalMrp",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }

            // 7. Decrease (-), 8. Selected Quantity, 9. Increase (+)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedQuantity > 1) selectedQuantity-- },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = JiptoGreenPrimary
                    )
                }
                Text(
                    text = "$selectedQuantity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { selectedQuantity++ },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = JiptoGreenPrimary
                    )
                }
            }

            // 10. Add to Cart Button (#16A34A Fresh Green)
            Button(
                onClick = { onAddToCart(selectedWeight.grams, selectedQuantity) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(
                    text = if (isHindi) "कार्ट में जोड़ें" else "Add to Cart",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Order Details Screen showing stored product photos from `itemsSnapshot`
 * and enforcing Order Immutability after confirmation.
 */
@Composable
fun JiptoOrderDetailsScreen(
    order: OrderRecord,
    isHindi: Boolean,
    onBack: () -> Unit,
    onTrackOrder: () -> Unit,
    onCancelOrder: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(if (isHindi) "वापस जाएं" else "Back to My Orders")
                }
                Button(
                    onClick = onTrackOrder,
                    colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary)
                ) {
                    Text(if (isHindi) "ऑर्डर ट्रैक करें" else "Track Order")
                }
            }
        }

        // Immutable Order Lock Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isHindi) "पुष्ट ऑर्डर विवरण — लॉक किया गया" else "Confirmed Order Snapshot — Locked & Read-Only",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Ordered products, weights, quantities, prices, discounts, totals, and delivery address are permanently locked.",
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Stored Product Photos & Price Snapshots
        items(order.itemsSnapshot, key = { "${order.orderId}_${it.productId}_${it.selectedWeightGrams}" }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(text = item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "${item.selectedUnitLabel} × ${item.quantity} · ₹${item.unitPrice}/unit",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(text = "₹${item.lineTotal}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        if (order.orderStatus == OrderStatus.ORDER_PLACED) {
            item {
                OutlinedButton(
                    onClick = onCancelOrder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isHindi) "ऑर्डर रद्द करें" else "Cancel Order")
                }
            }
        }
    }
}

/**
 * Google Maps Real Live Order Tracking Screen.
 * Shows customer destination marker, real partner GPS marker & route ONLY when `hasLiveGps == true`,
 * and visual order status timeline.
 */
@Composable
fun JiptoLiveOrderTrackingScreen(
    order: OrderRecord,
    trackingInfo: DeliveryTrackingInfo?,
    isHindi: Boolean,
    onBack: () -> Unit
) {
    val storeLatLng = LatLng(12.9784, 77.6408)
    val customerLatLng = LatLng(order.deliveryLat, order.deliveryLng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(customerLatLng, 14f)
    }

    LaunchedEffect(trackingInfo, order.deliveryLat, order.deliveryLng) {
        if (trackingInfo != null && trackingInfo.hasLiveGps) {
            val partnerLatLng = LatLng(trackingInfo.latitude, trackingInfo.longitude)
            val bounds = LatLngBounds.builder()
                .include(storeLatLng)
                .include(partnerLatLng)
                .build()
            try {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 120)
                )
            } catch (_: Exception) {
                val centerLat = (storeLatLng.latitude + partnerLatLng.latitude) / 2.0
                val centerLng = (storeLatLng.longitude + partnerLatLng.longitude) / 2.0
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(centerLat, centerLng),
                    13.5f
                )
            }
        } else {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(customerLatLng, 14f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Order #${order.orderId} · ${order.orderStatus.displayTitle}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onBack) {
                Text(if (isHindi) "वापस" else "Back")
            }
        }

        if (trackingInfo == null || !trackingInfo.hasLiveGps) {
            Text(
                text = if (isHindi)
                    "डिलीवरी पार्टनर का लाइव GPS अभी उपलब्ध नहीं है। मानचित्र पर आपका डिलीवरी स्थान दिखाया गया है।"
                else
                    "Live delivery partner location is not available yet. Showing your delivery destination on Google Maps.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Partner: ${trackingInfo.partnerName} (${trackingInfo.partnerPhone}) · Updated: ${trackingInfo.lastUpdatedAtIso}",
                fontSize = 12.sp,
                color = JiptoGreenPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp)),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(position = storeLatLng),
                title = "MY STORE Fulfillment Hub"
            )
            Marker(
                state = MarkerState(position = customerLatLng),
                title = "Customer Delivery Destination",
                snippet = order.deliveryAddress
            )
            if (trackingInfo != null && trackingInfo.hasLiveGps) {
                val partnerLatLng = LatLng(trackingInfo.latitude, trackingInfo.longitude)
                Marker(
                    state = MarkerState(position = partnerLatLng),
                    title = "Delivery Partner (${trackingInfo.partnerName})"
                )
                Polyline(
                    points = listOf(storeLatLng, partnerLatLng, customerLatLng),
                    color = JiptoGreenPrimary,
                    width = 8f
                )
            }
        }
    }
}

/**
 * Helper to download the real GitHub Actions release APK via Android's DownloadManager
 * and prompt Android's standard package installer once downloaded (never silent install).
 */
private fun downloadAndPromptInstallJiptoApk(
    context: Context,
    apkMetadata: ApkReleaseMetadata,
    onStatusUpdate: (String) -> Unit
) {
    if (!apkMetadata.isAvailable || !apkMetadata.downloadUrl.startsWith("https://")) {
        onStatusUpdate("APK is not available yet.")
        return
    }

    try {
        val fileName = apkMetadata.apkFileName.ifBlank { "jipto-customer-v${apkMetadata.versionName}-release.apk" }
        val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(publicDownloadsDir, fileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(apkMetadata.downloadUrl))
            .setTitle("JIPTO Customer APK (v${apkMetadata.versionName})")
            .setDescription("Downloading verified GitHub Actions release APK to Downloads folder")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        onStatusUpdate("Downloading $fileName (${apkMetadata.fileSizeFormatted}) to your phone's Downloads folder...")

        val onCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent?) {
                val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (completedId != downloadId) return
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }

                val downloadUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (downloadUri == null && !targetFile.exists()) {
                    onStatusUpdate("APK download failed or file was removed. Please tap Download again.")
                    return
                }

                // Guide user through Android's standard package installation flow
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    onStatusUpdate(
                        "APK saved to Downloads/$fileName. Please allow 'Install unknown apps', then tap Open/Install."
                    )
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(permissionIntent)
                    return
                }

                val apkUri = downloadUri ?: FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                onStatusUpdate("APK downloaded to Downloads/$fileName. Opening Android Package Installer...")
                context.startActivity(installIntent)
            }
        }

        ContextCompat.registerReceiver(
            context,
            onCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    } catch (e: Exception) {
        onStatusUpdate("Download error: ${e.localizedMessage ?: "Unable to start Android DownloadManager."}")
    }
}

@Composable
fun JiptoSettingsScreen(
    themeMode: ThemeMode,
    language: AppLanguageCode,
    githubRepoSlug: String,
    apkMetadata: ApkReleaseMetadata,
    isCheckingApk: Boolean,
    apkDownloadStatus: String,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectLanguage: (AppLanguageCode) -> Unit,
    onUpdateRepoSlug: (String) -> Unit,
    onCheckApkRelease: (String) -> Unit,
    onUpdateDownloadStatus: (String) -> Unit,
    onClose: () -> Unit
) {
    val isHindi = language == AppLanguageCode.HINDI
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isHindi) "सेटिंग्स" else "Settings",
                style = MaterialTheme.typography.headlineLarge
            )
            TextButton(onClick = onClose) {
                Text(if (isHindi) "बंद करें" else "Close")
            }
        }

        // Section A: Theme Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isHindi) "थीम सेटिंग्स" else "Theme Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onSelectTheme(mode) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.LIGHT -> if (isHindi) "लाइट मोड" else "Light Mode"
                                ThemeMode.DARK -> if (isHindi) "डार्क मोड" else "Dark Mode"
                                ThemeMode.SYSTEM -> if (isHindi) "सिस्टम डिफ़ॉल्ट" else "System Default"
                            }
                        )
                    }
                }
            }
        }

        // Section B: Language Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isHindi) "भाषा सेटिंग्स (Language)" else "Language Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AppLanguageCode.entries.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLanguage(lang) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == lang, onClick = { onSelectLanguage(lang) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = lang.displayName)
                    }
                }
            }
        }

        // Section C: App Installation / Download APK (Connected to real GitHub Actions Release APK)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isHindi) "ऐप इंस्टॉलेशन / APK डाउनलोड करें" else "App Installation / Download APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = githubRepoSlug,
                    onValueChange = onUpdateRepoSlug,
                    label = { Text("GitHub Repository (owner/repo)") },
                    placeholder = { Text("owner/jipto-android") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { onCheckApkRelease(githubRepoSlug) },
                    enabled = !isCheckingApk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isCheckingApk) "Checking GitHub Releases..."
                        else if (isHindi) "नवीनतम रिलीज़ जांचें"
                        else "Check Latest GitHub Release"
                    )
                }

                // Metadata rows: App version, APK file size, Build date, Download status
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "ऐप संस्करण (App Version)" else "App version",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (apkMetadata.isAvailable) "v${apkMetadata.versionName}" else "v${BuildConfig.VERSION_NAME}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "APK फ़ाइल आकार (File Size)" else "APK file size",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (apkMetadata.isAvailable) apkMetadata.fileSizeFormatted else "Not available",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "बिल्ड तिथि (Build Date)" else "Build date",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (apkMetadata.isAvailable) apkMetadata.buildDateFormatted else "Not available",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Download status and error message
                Surface(
                    color = if (apkMetadata.isAvailable) JiptoGreenPrimary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isHindi) "डाउनलोड स्थिति (Download Status)" else "Download status",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (apkMetadata.isAvailable) JiptoGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (apkMetadata.isAvailable) apkDownloadStatus else "APK is not available yet.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Download Latest JIPTO APK Button
                Button(
                    onClick = {
                        downloadAndPromptInstallJiptoApk(
                            context = context,
                            apkMetadata = apkMetadata,
                            onStatusUpdate = onUpdateDownloadStatus
                        )
                    },
                    enabled = apkMetadata.isAvailable && apkMetadata.downloadUrl.startsWith("https://"),
                    colors = ButtonDefaults.buttonColors(containerColor = JiptoGreenPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "नवीनतम JIPTO APK डाउनलोड करें" else "Download Latest JIPTO APK",
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isHindi)
                        "डाउनलोड पूरा होने के बाद, Android के सामान्य पैकेज इंस्टॉलर का उपयोग करके JIPTO इंस्टॉल करें।"
                    else
                        "After downloading, Android will open the standard Package Installer prompt so you can confirm and install the JIPTO APK.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
