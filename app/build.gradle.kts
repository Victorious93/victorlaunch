plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.victorious.launch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.victorious.launch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Dedicated debug keystore is gitignored. CI writes it from
        // VL_DEBUG_KEYSTORE_B64; local builds drop the same file in
        // keystore/victorlaunch-debug.jks. If it is missing we keep
        // AGP's default Android debug key and warn. See keystore/README.md.
        getByName("debug") {
            val dedicated = rootProject.file("keystore/victorlaunch-debug.jks")
            val envStore = System.getenv("VL_DEBUG_STORE_FILE")
            val store =
                when {
                    !envStore.isNullOrBlank() -> file(envStore)
                    dedicated.exists() -> dedicated
                    else -> null
                }
            if (store != null) {
                require(store.isFile) { "debug keystore not found: $store" }
                val storePasswordEnv = System.getenv("VL_DEBUG_STORE_PASSWORD")
                val keyPasswordEnv = System.getenv("VL_DEBUG_KEY_PASSWORD")
                require(!storePasswordEnv.isNullOrBlank()) {
                    "VL_DEBUG_STORE_PASSWORD is required when using $store"
                }
                require(!keyPasswordEnv.isNullOrBlank()) {
                    "VL_DEBUG_KEY_PASSWORD is required when using $store"
                }
                storeFile = store
                storePassword = storePasswordEnv
                keyAlias = System.getenv("VL_DEBUG_KEY_ALIAS") ?: "victorlaunchdebug"
                keyPassword = keyPasswordEnv
            } else {
                logger.warn(
                    "keystore/victorlaunch-debug.jks is missing; signing with the default Android debug key. " +
                        "The APK will not match the published debug cert (keystore/README.md).",
                )
            }
        }
        // Release signing is read ONLY from environment variables / CI secrets.
        // No release keystore is ever committed to this repo.
        val releaseStore = System.getenv("VL_RELEASE_STORE_FILE")
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("VL_RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("VL_RELEASE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("VL_RELEASE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
        aidl = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("docs"))
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":shizuku-embed"))
    implementation(project(":mods-embed"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
