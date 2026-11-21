import java.nio.file.Files
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing: KEYSTORE_BASE64 (CI) or KEYSTORE_FILE (local path), plus KEYSTORE_PASSWORD,
// KEY_ALIAS, KEY_PASSWORD. Each is read from the environment first, then local.properties.
// Never commit keys. Without all of them the release build is left unsigned.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(name: String): String? =
    (System.getenv(name) ?: localProps.getProperty(name))?.takeIf { it.isNotBlank() }

val signingSecrets = listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
val keystoreConfigured = signingValue("KEYSTORE_BASE64") != null || signingValue("KEYSTORE_FILE") != null
val signingAnySet = keystoreConfigured || signingSecrets.any { signingValue(it) != null }
val signingSecretsSet = signingSecrets.all { signingValue(it) != null }

// Only materialise a keystore when everything else is present. KEYSTORE_BASE64 is decoded to a
// private (owner-only, 0600) temp file outside the project that is deleted when the JVM exits.
// In CI prefer KEYSTORE_FILE (see ci-templates/release.yml).
val signingKeystore: File? = if (!signingSecretsSet) null else run {
    val b64 = signingValue("KEYSTORE_BASE64")
    val path = signingValue("KEYSTORE_FILE")
    when {
        b64 != null -> Files.createTempFile("release-", ".keystore").toFile().also {
            it.deleteOnExit()
            it.writeBytes(Base64.getMimeDecoder().decode(b64))
        }
        path != null -> rootProject.file(path).takeIf { it.exists() }
        else -> null
    }
}
val signingComplete = signingKeystore != null
if (signingAnySet && !signingComplete) {
    logger.warn(
        "WARNING: release signing is only partially configured (need KEYSTORE_BASE64 or KEYSTORE_FILE " +
            "plus KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD); the release build will be UNSIGNED.",
    )
}

android {
    namespace = "com.postpole.spoonfile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.postpole.spoonfile"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // One-time in-app product id for the Pro unlock. Override with -PproProductId=... if needed.
        val proProductId = providers.gradleProperty("proProductId").getOrElse("lifetime_full_access")
        buildConfigField("String", "PRO_PRODUCT_ID", "\"$proProductId\"")
    }

    // Ad units. Debug / internal builds use ONLY Google's official demo units
    // (https://developers.google.com/admob/android/test-ads); release uses the production units.
    // Never click production ads.
    fun adUnits(banner: Map<String, String>, interstitial: String, rewarded: String) = mapOf(
        "AD_UNIT_BANNER_RECIPES" to banner.getValue("recipes"),
        "AD_UNIT_BANNER_SHOPPING" to banner.getValue("shopping"),
        "AD_UNIT_BANNER_CATEGORIES" to banner.getValue("categories"),
        "AD_UNIT_INTERSTITIAL_AFTER_RECIPE" to interstitial,
        "AD_UNIT_REWARDED" to rewarded,
    )

    val demoBanner = "ca-app-pub-3940256099942544/9214589741"
    val debugUnits = adUnits(
        banner = mapOf("recipes" to demoBanner, "shopping" to demoBanner, "categories" to demoBanner),
        interstitial = "ca-app-pub-3940256099942544/1033173712",
        rewarded = "ca-app-pub-3940256099942544/5224354917",
    )
    val releaseUnits = adUnits(
        banner = mapOf(
            "recipes" to "ca-app-pub-2007361247232604/2557681771", // sf_banner_recipes
            "shopping" to "ca-app-pub-2007361247232604/8931518430", // sf_banner_shopping
            "categories" to "ca-app-pub-2007361247232604/3735032070", // sf_banner_categories
        ),
        interstitial = "ca-app-pub-2007361247232604/1526516180", // sf_interstitial_after_recipe
        rewarded = "", // sf_rewarded stays unused: ads_rewarded_enabled is false and there is no rewarded UI
    )

    signingConfigs {
        if (signingComplete) {
            create("release") {
                storeFile = signingKeystore
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // UMP debug geography / test device (only honoured in debug builds).
            val geography = providers.gradleProperty("umpDebugGeography").getOrElse("")
            val testDevice = providers.gradleProperty("umpTestDeviceHash").getOrElse("")
            buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"$geography\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"$testDevice\"")
            debugUnits.forEach { (name, id) -> buildConfigField("String", name, "\"$id\"") }
        }
        release {
            buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
            releaseUnits.forEach { (name, id) -> buildConfigField("String", name, "\"$id\"") }
            if (signingComplete) signingConfig = signingConfigs.getByName("release")
            // Native symbols for Crashlytics / Play Console (no-op when the app has no native code).
            ndk.debugSymbolLevel = "FULL"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Firebase plugins are applied only when this app has its own google-services.json, so the bare
// template (and CI) builds without it. Analytics falls back to a no-op in that case.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    // Upload the R8 mapping file (and native symbols) for release builds.
    android.buildTypes.named("release") {
        (this as ExtensionAware).extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension>(
            "firebaseCrashlytics",
        ) {
            mappingFileUploadEnabled = true
            nativeSymbolUploadEnabled = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.billing.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.mlkit.text.recognition)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
