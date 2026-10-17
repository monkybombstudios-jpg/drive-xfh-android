plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "za.co.xfh.drive"
    compileSdk = 35
    splits { abi { isEnable = true; reset(); include("arm64-v8a","armeabi-v7a"); isUniversalApk = true } }
    defaultConfig { applicationId = "za.co.xfh.drive"; minSdk = 26; targetSdk = 33; versionCode = 20; versionName = "0.16.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("org.osmdroid:osmdroid-android:6.1.20"); implementation("org.maplibre.gl:android-sdk:11.8.0"); implementation("androidx.documentfile:documentfile:1.0.1") }
