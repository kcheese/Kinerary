import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun localProperties(name: String) = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use(::load)
}

// Release signing key, kept out of git
val keystoreProperties = localProperties("keystore.properties")

// The sync server's address is personal, so it lives in local.properties (kinerary.sync-server=https://...)
val syncServer = localProperties("local.properties").getProperty("kinerary.sync-server").orEmpty()

android {
    namespace = "com.kyle.kinerary"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.kyle.kinerary"
        minSdk = 26
        targetSdk = 36
        versionCode = 101
        versionName = "1.0.1"

        buildConfigField("String", "SYNC_SERVER", "\"$syncServer\"")
        // GitHub repo whose releases the app updates from (empty turns the updater off)
        buildConfigField(
            "String",
            "UPDATE_REPO",
            "\"${providers.gradleProperty("kinerary.update-repo").getOrElse("")}\"",
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
}
