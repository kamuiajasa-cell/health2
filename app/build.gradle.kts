plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val health2KeystorePath = System.getenv("HEALTH2_KEYSTORE_PATH")
val health2StorePassword = System.getenv("HEALTH2_KEYSTORE_PASSWORD")
val health2KeyAlias = System.getenv("HEALTH2_KEY_ALIAS")
val health2KeyPassword = System.getenv("HEALTH2_KEY_PASSWORD")
val health2ReleaseSigningReady = listOf(health2KeystorePath, health2StorePassword, health2KeyAlias, health2KeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.health2.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.health2.companion"
        minSdk = 29
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }
    signingConfigs {
        if (health2ReleaseSigningReady) {
            create("release") {
                storeFile = file(health2KeystorePath!!)
                storePassword = health2StorePassword
                keyAlias = health2KeyAlias
                keyPassword = health2KeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (!health2ReleaseSigningReady) {
                throw GradleException("Health2 release signing is not configured. Add the HEALTH2_KEYSTORE_* GitHub Actions secrets.")
            }
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":huawei"))
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
