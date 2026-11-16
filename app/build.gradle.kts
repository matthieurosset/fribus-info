import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// versionName = tag git exact (v1.2.3 -> 1.2.3), sinon 0.1.0
fun gitTagVersion(): String = try {
    val p = ProcessBuilder("git", "describe", "--tags", "--exact-match", "--match", "v*")
        .directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0 && out.startsWith("v")) out.removePrefix("v") else "0.1.0"
} catch (e: Exception) {
    "0.1.0"
}

android {
    namespace = "ch.fribusinfo"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.fribusinfo"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("FRIBUS_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = gitTagVersion()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val file = keystoreProps.getProperty("storeFile") ?: System.getenv("FRIBUS_KEYSTORE")
            if (file != null) {
                storeFile = rootProject.file(file)
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: System.getenv("FRIBUS_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "fribus"
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: System.getenv("FRIBUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
