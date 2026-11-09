import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val devProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun devValue(name: String): String =
    "\"" + devProperties.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.pocketagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pocketagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "POCKETAGENT_KIND", devValue("pocketagent.kind"))
            buildConfigField("String", "POCKETAGENT_BASE_URL", devValue("pocketagent.baseUrl"))
            buildConfigField("String", "POCKETAGENT_API_KEY", devValue("pocketagent.apiKey"))
            buildConfigField("String", "POCKETAGENT_MODEL", devValue("pocketagent.model"))
            buildConfigField("String", "POCKETAGENT_SUPPORTS_IMAGES", devValue("pocketagent.supportsImages"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
