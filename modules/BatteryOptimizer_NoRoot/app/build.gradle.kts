plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.batteryopt.noroot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.batteryopt.noroot"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Xposed API (compileOnly, LSPatch运行时不打包进APK)
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

    // Gson (配置文件序列化)
    implementation("com.google.code.gson:gson:2.10.1")

    // 注意: NoRoot 版不依赖 Shizuku，仅做应用层自身耗电优化
}
