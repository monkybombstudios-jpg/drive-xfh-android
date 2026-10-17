plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "za.co.xfh.drive"
    compileSdk = 35
    defaultConfig { applicationId = "za.co.xfh.drive"; minSdk = 26; targetSdk = 33; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
