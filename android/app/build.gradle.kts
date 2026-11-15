plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Apply google-services plugin automatically when google-services.json is present
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.jipto.customer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jipto.customer"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        versionName = System.getenv("JIPTO_VERSION_NAME")?.ifBlank { "1.0.0" } ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Inject repository slug from GitHub Actions environment so Settings -> Download APK
        // automatically queries the exact GitHub repository's releases endpoint.
        val repoSlug = System.getenv("GITHUB_REPOSITORY")?.ifBlank { "kushdas061-star/jipto-customer-android" }
            ?: "kushdas061-star/jipto-customer-android"
        buildConfigField("String", "GITHUB_REPO_SLUG", "\"$repoSlug\"")

        // Shared JIPTO & MY STORE Firebase backend configuration
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"confident-env-q01dl\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"1:493196660765:web:213c16ca36fc50c93d36f4\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyAJk5rpLcl4aDSGnrSkk6F0Idw-Vzyv9Zk\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"confident-env-q01dl.firebasestorage.app\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"493196660765\"")
        buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"ai-studio-0fb04cee-89a5-4f02-bf35-ab52973be375\"")

        // Safe fallback for MAPS_API_KEY manifest placeholder when building in CI
        val mapsKey = System.getenv("GOOGLE_MAPS_API_KEY")?.ifBlank { "AIzaSyJiptoMapsPlaceholder" }
            ?: "AIzaSyJiptoMapsPlaceholder"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    signingConfigs {
        create("release") {
            // Signing credentials are read strictly from environment variables in CI.
            // Never hardcode keystore files, passwords, or private keys in source code.
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val keyAliasVal = System.getenv("ANDROID_KEY_ALIAS")
            val keyPasswordVal = System.getenv("ANDROID_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAliasVal.isNullOrBlank() &&
                !keyPasswordVal.isNullOrBlank() &&
                file(keystorePath).exists()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Coil for efficient image loading with placeholders
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Firebase BOM (Shared backend with MY STORE Admin App)
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Google Maps SDK for Android (19.2.0+ includes MapsApiSettings) & Jetpack Compose Maps
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
}
