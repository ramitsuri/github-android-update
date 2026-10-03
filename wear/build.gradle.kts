plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Can be overridden by project properties VERSION_CODE and VERSION_NAME (e.g. from CI).
val autoVersionCode = project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 1
val autoVersionName = project.findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

android {
    namespace = "com.ramitsuri.githubandroidupdate.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ramitsuri.githubandroidupdate"
        minSdk = 26
        targetSdk = 37
        versionCode = autoVersionCode
        versionName = autoVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            //signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
}
