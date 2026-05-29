plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mingyuan.flyto.lander"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mingyuan.flyto.lander"
        minSdk = 33
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.1-rc2"
    }

    // signingConfig：CI 透過 -PRELEASE_STORE_FILE 等 properties 注入 keystore；
    // 本機開發或 secret 未設定時，properties 全為 null，release build 會 unsigned
    // （unsigned APK 無法 install 但 lint / assemble 仍可跑）。
    signingConfigs {
        create("release") {
            val storePath = project.findProperty("RELEASE_STORE_FILE") as String?
            val storePass = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            val keyAliasProp = project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPass = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            if (storePath != null && storePass != null && keyAliasProp != null && keyPass != null) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = keyAliasProp
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只在 properties 真的提供時才掛 signingConfig，否則留空產出 unsigned APK
            if (project.findProperty("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    // Reproducible build: strip timestamps & ordering from APK metadata.
    // 配合 §7.2 reproducible build 約束（docs/android-platform.md）
    androidResources {
        generateLocaleConfig = false
    }

    lint {
        // CI 與本機都必須擋下 lint error，避免再次發生「Phase 1 從未跑過 lint」的盲點
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true

        // MockLocation：本 App 的核心存在理由就是 mock location，release build 必須持有
        // ACCESS_MOCK_LOCATION。這條 lint rule 假設 mock 僅供 test/debug，不適用 Lander。
        disable += "MockLocation"

        // 工具版本保守策略 explicit 聲明，避免 lint 每次提示「有更新版本」干擾：
        // - AGP 維持 8.13.2（避開 9.x major bump 的 build script breaking 風險）
        // - Kotlin 維持 2.2.x（2.3.x 是 2025 末新版，Compose Compiler 對齊待社群驗證）
        // 升級決策由人類掌握；版本是否該升，應透過定期 review 而非 lint 提醒。
        disable += "AndroidGradlePluginVersion"
        disable += "NewerVersionAvailable"
    }
}

dependencies {
    // §7.4 約束：零第三方依賴；僅允許 AndroidX core + Compose 官方家族
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
}
