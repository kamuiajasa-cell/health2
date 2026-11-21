plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.joyodigitama.huaweiwear"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "DISTRIBUTION", "\"sideload\"")
    }
    buildFeatures { buildConfig = true }
    sourceSets {
        getByName("main") {
            manifest.srcFile("../vendor/RunNowNative/huawei/src/main/AndroidManifest.xml")
            java.srcDirs("../vendor/RunNowNative/huawei/src/main/java")
            res.srcDirs("../vendor/RunNowNative/huawei/src/main/res")
            assets.srcDirs("../vendor/RunNowNative/huawei/src/main/assets")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.activity:activity:1.10.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
}
