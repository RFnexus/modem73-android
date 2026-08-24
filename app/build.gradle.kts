import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Locate the modem73 C++ core: local.properties `modem73.core.dir`, else third_party/modem73.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val modem73CoreDir: File = (localProps.getProperty("modem73.core.dir")
    ?: System.getenv("MODEM73_CORE_DIR"))
    ?.let { file(it) }
    ?: rootProject.file("third_party/modem73")

android {
    namespace = "app.modem73"
    compileSdk = 37
    ndkVersion = "28.2.13676358"   // AGP 9.3.1 default NDK (r28c); keep in sync with docs/SETUP.md

    defaultConfig {
        applicationId = "app.modem73"
        minSdk = 28          // Android 9: AAudio is mature, USB host + FGS types are all available
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64 = every real phone incl. Pixel 7a; x86_64 = the emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DMODEM73_ROOT=${modem73CoreDir.absolutePath}",
                )
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // keep native symbols for ndk-stack / lldb
            isJniDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs { useLegacyPackaging = false }   // page-aligned, uncompressed .so (16 KB page size ready)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Serial PTT over USB OTG (FTDI, CP210x, CH34x, CDC-ACM/AIOC): setRTS()/setDTR()
    implementation(libs.usbserial.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
