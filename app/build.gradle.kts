plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mingyuan.flyto.lander"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mingyuan.flyto.lander"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
