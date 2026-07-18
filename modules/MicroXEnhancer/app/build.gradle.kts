// 微X增强模块 — 应用级构建配置
// 编译目标：LSPatch免Root环境，仅引入LSPosed API
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.microx.enhancer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.microx.enhancer"
        minSdk = 27          // Android 8.1，LSPatch最低要求
        targetSdk = 34       // Android 14
        versionCode = 1      // LSPatch通过版本号识别版本更新
        versionName = "1.0.0"
        multiDexEnabled = false
    }

    // 构建特性：关闭混淆以兼容Hook框架类查找
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // 不需要签名配置，LSPatch集成模式会重打包
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    // Java 8兼容（LSPosed API基于此）
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // 构建特性：关闭BuildConfig生成（减小体积）
    buildFeatures {
        buildConfig = false
    }
}

// ===== 依赖配置：仅引入LSPosed API，不包含任何Root依赖 =====
dependencies {
    // LSPosed API：provided编译方式，不打包进APK
    // LSPatch环境中由框架自身提供
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    // AndroidX基础UI库（设置界面）
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")

    // Kotlin标准库
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
