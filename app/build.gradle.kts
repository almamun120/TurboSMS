plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

// ── Version (CI থেকে -PversionName / -PversionCode দিয়ে ওভাররাইড হয়) ─────
val appVersionName: String = (project.findProperty("versionName") as String?)
    ?: (project.findProperty("APP_VERSION_NAME") as String?) ?: "1.0.0"
val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// ── Signing (env থেকে; না থাকলে release আনসাইনড থাকবে) ──────────────────────
val ksFile: String? = System.getenv("KEYSTORE_FILE")
val hasSigning = !ksFile.isNullOrBlank() && file(ksFile).exists()

android {
    namespace = "com.turbosms"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.turbosms"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/**",
                "META-INF/AL2.0", "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // শুধু পুরনো .xls পড়ার জন্য (HSSF). .xlsx আমরা নিজেরা পার্স করি (XlsxReader.kt),
    // কারণ poi-ooxml Android-এ javax.xml.stream না থাকায় ক্র্যাশ করে।
    implementation("org.apache.poi:poi:5.2.3")

    // NanoHTTPD for embedded web server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
