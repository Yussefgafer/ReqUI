plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    signingConfigs {
        create("releaseConfig") {
            val keystorePropsFile = File(rootDir, "keystore.properties")
            val keystoreProps = if (keystorePropsFile.exists()) {
                keystorePropsFile.readLines()
                    .filter { it.isNotBlank() && '=' in it }
                    .associate { line ->
                        val (k, v) = line.split("=", limit = 2)
                        k.trim() to v.trim()
                    }
            } else emptyMap()
            storeFile = file(keystoreProps["STORE_FILE"] ?: "requi.jks")
            storePassword = keystoreProps["STORE_PASSWORD"] ?: (System.getenv("KEYSTORE_PASSWORD") ?: "")
            keyAlias = keystoreProps["KEY_ALIAS"] ?: (System.getenv("KEY_ALIAS") ?: "requi")
            keyPassword = keystoreProps["KEY_PASSWORD"] ?: (System.getenv("KEY_PASSWORD") ?: "")
        }
    }
    namespace = "com.android.requi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.android.requi"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        multiDexEnabled = false
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("releaseConfig")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Signed with the default debug keystore; only release uses releaseConfig.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            excludes += "**/libandroidx.graphics.path.so"
        }
        resources {
            excludes += "**/libandroidx.graphics.path.so"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)




// these are optional but may be needed
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.lifecycle.runtime.ktx)
//    implementation(libs.androidx.compose.foundation)
//    implementation(libs.androidx.compose.ui)


//    implementation(libs.androidx.compose.ui.graphics)
//    implementation(libs.androidx.compose.ui.tooling.preview)
//    testImplementation(libs.junit)
//    androidTestImplementation(platform(libs.androidx.compose.bom))
//    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
//    androidTestImplementation(libs.androidx.espresso.core)
//    androidTestImplementation(libs.androidx.junit)
//    debugImplementation(libs.androidx.compose.ui.test.manifest)
//    debugImplementation(libs.androidx.compose.ui.tooling)
}