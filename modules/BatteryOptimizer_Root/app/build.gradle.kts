plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.batteryopt.pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.batteryopt.pro"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

    // Gson (配置文件序列化)
    implementation("com.google.code.gson:gson:2.10.1")

    // Shizuku API (compileOnly, 反射调用兼容本地模式)
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    compileOnly("dev.rikka.shizuku:provider:13.1.5")
}
