plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.phoenix.vpn"
    compileSdk = 35
    defaultConfig { applicationId = "com.phoenix.vpn"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("com.zaneschepke:hevtunnel:1.0.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
