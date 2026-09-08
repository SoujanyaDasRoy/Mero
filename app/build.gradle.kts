import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Signing credentials come from a gitignored keystore.properties beside this
// file's project root, never from literals here. Absent the file the release
// build still assembles, just unsigned — enough to check that R8 has not
// broken anything, not enough to hand to anyone.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.mero"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mero"
        minSdk = 28
        targetSdk = 36
        versionCode = 10
        versionName = "1.6.0"
    }

    // yt-dlp ships a Python runtime and ffmpeg per architecture, so a universal
    // APK lands around 263 MB. Splitting by ABI gets a real phone down to one
    // architecture's worth. arm64-v8a is what every modern handset needs;
    // x86_64 exists for the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            // The same keystore has to sign every release: a different one makes
            // Android treat the APK as a different app, and the only way out for
            // a friend is uninstalling and losing their library.
            val store = keystoreProperties.getProperty("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
                .takeIf { keystoreProperties.getProperty("storeFile") != null }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":innertube"))

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.jtransforms)
    implementation(libs.kotlinx.coroutines.guava)

    // Escape hatch: innertube's /player is currently rejected by YouTube
    // (missing PO token support, upstream issue z-huang/InnerTune#1748, open
    // since Dec 2024). yt-dlp actively maintains PO token generation, so it's
    // the primary stream-URL source until that's fixed upstream. See
    // .claude/skills/resync-innertube and PRD §9.
    implementation(libs.youtubedl.library)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Drag-to-reorder for the queue. InnerTune uses this same library.
    implementation(libs.reorderable)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.palette)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
