plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

// Release builds are signed in CI only (see .github/workflows/release.yml and
// RELEASING.md). The keystore and its passwords come from environment variables
// backed by GitHub secrets; they are never in the repo. Without them,
// assembleRelease still works and signs with the debug key, for testing only.
val releaseKeystore = providers.environmentVariable("STASH_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.foxsoft2005.stash"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.foxsoft2005.stash"
        minSdk = 26          // ML Kit GenAI Prompt API requires API 26+
        targetSdk = 37
        // Set from the git tag by the release workflow (v1.2.3 -> "1.2.3", 1002003).
        versionCode = providers.environmentVariable("STASH_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("STASH_VERSION_NAME").orNull ?: "0.1.0-dev"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("STASH_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("STASH_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("STASH_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real releases: the release key (CI only). Everywhere else: the debug key, so
            // the R8-shrunk build can still be installed and tested. Such APKs have a
            // different signature, and release.yml refuses to publish anything that
            // isn't signed with the real release certificate.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lists the app's languages (from values-*/ folders) for Android 13+'s
    // per-app language setting, so Stash can be Russian on an English phone.
    androidResources {
        generateLocaleConfig = true
    }
}

// Room writes a JSON snapshot of every schema version here. Commit these files:
// they're what migrations are validated (and auto-migrations generated) against.
// Room 3 always generates Kotlin, so the old room.generateKotlin KSP arg is gone.
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle / ViewModel scoped to Navigation 3 entries
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Room 3 (KMP-first, suspend/Flow-only) with the bundled SQLite driver
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)

    // CameraX with the Compose-native viewfinder
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface) // reads photo orientation before AI tagging

    // Coroutines / serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // On-device AI: Gemini Nano via ML Kit GenAI Prompt API, with ML Kit labeling as fallback
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mlkit.image.labeling)

    testImplementation(libs.junit)
}
