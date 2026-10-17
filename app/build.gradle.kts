plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "za.co.xfh.drive"
    compileSdk = 35
    defaultConfig { applicationId = "za.co.xfh.drive"; minSdk = 26; targetSdk = 33; versionCode = 10; versionName = "0.10.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("org.osmdroid:osmdroid-android:6.1.20") }
