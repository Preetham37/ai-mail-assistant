import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.preetham.aimailassistant"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.preetham.aimailassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Safely resolve Google OAuth Client ID from local.properties or gmail/credentials.json
        val localPropsFile = project.rootProject.file("local.properties")
        val localProps = Properties()
        if (localPropsFile.exists()) {
            FileInputStream(localPropsFile).use { localProps.load(it) }
        }

        var oauthClientId = localProps.getProperty("oauth.client.id") ?: ""
        if (oauthClientId.isBlank()) {
            val credsFile = file("${project.rootDir}/../gmail/credentials.json")
            if (credsFile.exists()) {
                val match = Regex("\"client_id\"\\s*:\\s*\"([^\"]+)\"").find(credsFile.readText())
                oauthClientId = match?.groupValues?.get(1) ?: ""
            }
        }
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"$oauthClientId\"")

        // Safely resolve Gemini API Key from local.properties, root .env, or environment
        var geminiApiKey = localProps.getProperty("gemini.api.key") ?: System.getenv("GEMINI_API_KEY") ?: ""
        if (geminiApiKey.isBlank()) {
            val rootEnvFile = file("${project.rootDir}/../.env")
            if (rootEnvFile.exists()) {
                val match = Regex("GEMINI_API_KEY\\s*=\\s*([^\\r\\n]+)").find(rootEnvFile.readText())
                geminiApiKey = match?.groupValues?.get(1)?.trim() ?: ""
            }
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        val clientPrefix = oauthClientId.substringBefore(".apps.googleusercontent.com")
        val redirectScheme = if (clientPrefix.isNotBlank() && clientPrefix != oauthClientId) {
            "com.googleusercontent.apps.$clientPrefix"
        } else {
            "com.preetham.aimailassistant"
        }
        manifestPlaceholders["appAuthRedirectScheme"] = redirectScheme

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.gson)
    implementation(libs.openid.appauth)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
