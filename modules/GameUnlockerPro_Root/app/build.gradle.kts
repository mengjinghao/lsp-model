plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gameunlocker.pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gameunlocker.pro"
        minSdk = 27
        targetSdk = 34
        versionCode = 20260701
        versionName = "1.0.0"

        // 声明为Xposed模块，LSPatch本地模式兼容
        metaData {
            putString("xposedminversion", "82")
            putString("xposeddescription", "Game-Unlocker Pro - 超强游戏帧率解锁模块")
            putBoolean("xposedscope", true)
        }
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
            isDebuggable = true
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
    // Xposed API (compileOnly, 运行时由框架提供)
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    // AndroidX 核心库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

    // Gson (用于配置文件序列化)
    implementation("com.google.code.gson:gson:2.10.1")

    // Shizuku API (compileOnly, 本地模式通过反射调用)
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    compileOnly("dev.rikka.shizuku:provider:13.1.5")
}
