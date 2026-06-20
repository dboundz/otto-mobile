import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.reader())
}

val userGradlePropertiesFile = file("${System.getProperty("user.home")}/.gradle/gradle.properties")
val userGradleProperties = Properties()
if (userGradlePropertiesFile.exists()) {
    userGradleProperties.load(userGradlePropertiesFile.reader())
}

val mapboxAccessToken =
    listOfNotNull(
        project.findProperty("MAPBOX_ACCESS_TOKEN") as String?,
        localProperties.getProperty("MAPBOX_ACCESS_TOKEN"),
        userGradleProperties.getProperty("MAPBOX_ACCESS_TOKEN"),
        System.getenv("MAPBOX_ACCESS_TOKEN"),
    ).firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
        .orEmpty()

val klipyAppKey =
    ((project.findProperty("KLIPY_APP_KEY") as String?)
        ?: localProperties.getProperty("KLIPY_APP_KEY"))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Sk72QNgF11rTpWOtLgW32nStQsuyEfskKYniumWNucmpDtXDxEtSnWf9rmkKOAWD"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.reader())
}

android {
    namespace = "to.ottomot.driftd"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "to.ottomot.driftd"
        minSdk = 24
        targetSdk = 36
        versionCode = 91
        versionName = "1.0.91"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /** Production Otto API — same hosts as shipping iOS. */
        buildConfigField("String", "API_BASE_URL", "\"https://api.ottomot.to/\"")
        buildConfigField("String", "WEBSOCKET_URL", "\"wss://rt.ottomot.to/ws\"")

        /** Public Mapbox runtime token. Keep the value in ~/.gradle/gradle.properties or CI secrets. */
        val escapedMapboxToken = mapboxAccessToken.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$escapedMapboxToken\"")
        resValue("string", "mapbox_access_token", mapboxAccessToken.ifEmpty { " " })

        /** Public KLIPY app key for chat GIF search. Keep overrides in Gradle properties or local.properties. */
        val escapedKlipyAppKey = klipyAppKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "KLIPY_APP_KEY", "\"$escapedKlipyAppKey\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(requireNotNull(keystoreProperties.getProperty("storeFile")))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.compose)
    implementation(libs.mapbox.maps.androidauto) {
        // The app uses Mapbox's NDK 27 artifacts for 16 KB page-size support. The Android Auto
        // maps extension publishes only a default artifact, so keep the extension classes but bind
        // them to the already-present NDK 27 Maps runtime to avoid duplicate Mapbox classes.
        exclude(group = "com.mapbox.maps")
        exclude(group = "com.mapbox.plugin")
        exclude(group = "com.mapbox.common")
        exclude(group = "com.mapbox.module")
        exclude(group = "com.mapbox.extension", module = "maps-style")
        exclude(group = "com.mapbox.extension", module = "maps-localization")
    }
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.yalantis.ucrop)
    implementation(libs.androidx.exifinterface)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    /** Long-press drag reorder for Garage (LazyColumn). */
    implementation("sh.calvin.reorderable:reorderable:2.3.3")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

fun deleteAppleDoubleBuildSidecars(includeDexOnly: Boolean = false) {
    val patterns =
        if (includeDexOnly) {
            listOf("**/._*.dex")
        } else {
            listOf("**/._*")
        }
    delete(
        fileTree(layout.buildDirectory) {
            include(patterns)
        },
    )
}

// Building from an external Mac volume can create AppleDouble sidecars next to generated dex files.
// R8 treats `._*.dex` as real dex archives, so strip them immediately before dex merge tasks.
tasks.configureEach {
    if (name.startsWith("merge") && name.contains("Dex")) {
        doFirst {
            deleteAppleDoubleBuildSidecars(includeDexOnly = true)
        }
    }
}