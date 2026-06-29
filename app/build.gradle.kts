import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // The google-services plugin is applied conditionally at the bottom of this file — only
    // when app/google-services.json is present — so the build works without it and activates
    // automatically once you add your Firebase config.
}

// Release signing credentials are read from a gitignored keystore.properties at the repo root
// (see keystore.properties.example). When the file is absent — debug builds, CI without
// secrets — the release signing config is simply not applied, so the project still builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.pdfmaster.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pdfmaster.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Distinct id so debug/release can coexist on one device — BUT only when there's no
            // google-services.json. A release-only Firebase config has no client for the
            // "<id>.debug" package, which makes the Google Services plugin fail; with Firebase
            // configured, debug uses the base applicationId so it matches the single client.
            // (To run debug + release at once, add a debug app in Firebase and a per-variant
            // google-services.json under app/src/debug/.)
            if (!file("google-services.json").exists()) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-debug"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Applied only when keystore.properties is present; otherwise the release build is
            // unsigned (fine for `assembleRelease` locally, but you must add the keystore before
            // uploading to Play). See keystore.properties.example.
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")  // kept for AppCompat-based library UI
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Coil for images
    implementation(libs.coil.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)

    // PDF Libraries
    implementation(libs.pdfbox.android)  // PDFBox-Android: text extraction + stamping/page ops
    implementation(libs.mupdf.fitz)       // MuPDF for accurate text extraction
    // NOTE: OpenPDF (com.lowagie) was removed — it references java.awt.* which doesn't exist on
    // Android, so it crashed at runtime. All PDF stamping/page ops now use PDFBox-Android.

    // NOTE: ComPDFKit was removed — it was gated off (USE_COMPDFKIT=false), never invoked, and
    // added ~30MB + a paid license requirement. The app uses its own PDFBox-based editor.

    // Accompanist
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // Reorderable for drag-drop
    implementation(libs.reorderable)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ads & Billing
    // implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)

    // Firebase Analytics (BoM-managed). Compiles/runs without google-services.json;
    // FirebaseAnalyticsTracker falls back to Logcat until the config is present.
    // NOTE: Crashlytics is intentionally NOT included — its SDK requires the Crashlytics Gradle
    // plugin (build-ID generation) or FirebaseInitProvider crashes at startup. Add both the
    // firebase-crashlytics dependency AND apply("com.google.firebase.crashlytics") together if
    // you want crash reporting later.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// Apply the Firebase google-services plugin only when the config is actually present, so the
// project builds out-of-the-box without it. Drop app/google-services.json in and the next build
// processes it automatically (no further Gradle edits needed). The plugin is on the classpath
// via the root build.gradle.kts `apply false` declaration.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
