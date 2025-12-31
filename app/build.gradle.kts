plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
}

import java.util.Properties

android {
    namespace = "com.burak.zonesilent"
    compileSdk = 35

    val localProperties = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            f.inputStream().use { load(it) }
        }
    }

    fun prop(name: String): String? {
        return project.findProperty(name)?.toString() ?: localProperties.getProperty(name)
    }

    val signingKeyPath = prop("KEY_PATH")
    val signingKeyPassword = prop("KEY_PASSWORD")
    val signingKeyAlias = prop("KEY_ALIAS")

    fun validateAdmobAppId(raw: String?): String {
        val trimmed = (raw ?: "").trim()
        val pattern = Regex("^ca-app-pub-\\d{16}~\\d{10}$")
        return if (!pattern.matches(trimmed)) {
            "ca-app-pub-3940256099942544~3347511713"
        } else {
            trimmed
        }
    }

    fun propBool(name: String, default: Boolean = false): Boolean {
        val raw = prop(name) ?: return default
        return raw.trim().equals("true", ignoreCase = true)
    }

    defaultConfig {
        applicationId = "com.burak.zonesilent"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders += mapOf(
            "MAPS_API_KEY" to (prop("MAPS_API_KEY") ?: ""),
            "ADMOB_APP_ID" to validateAdmobAppId(
                prop("ADMOB_APP_ID_RELEASE") ?: prop("ADMOB_APP_ID")
            )
        )

        resValue(
            "bool",
            "force_test_ads",
            if (propBool("FORCE_TEST_ADS", default = false)) "true" else "false"
        )
    }

    signingConfigs {
        if (signingKeyPath != null && signingKeyPassword != null && signingKeyAlias != null) {
            create("release") {
                storeFile = file(signingKeyPath)
                storePassword = signingKeyPassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            if (signingKeyPath != null && signingKeyPassword != null && signingKeyAlias != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // Core AndroidX (compileSdk 34 uyumlu sabit sürümler)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    // UI
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Google Play Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Room (KSP)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}