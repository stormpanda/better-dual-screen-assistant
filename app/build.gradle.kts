import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> props.load(stream) }
}

android {
    namespace = "com.stormpanda.betterdualscreenassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stormpanda.betterdualscreenassistant"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFileProp = localProperties.getProperty("bdsa.keystore.file")
                ?: localProperties.getProperty("megingiard.keystore.file")
            val keyPasswordProp = localProperties.getProperty("bdsa.keystore.key.password")
                ?: localProperties.getProperty("megingiard.keystore.key.password")
            val storePasswordProp = localProperties.getProperty("bdsa.keystore.password")
                ?: localProperties.getProperty("megingiard.keystore.password")
            val aliasProp = localProperties.getProperty("bdsa.keystore.alias")
                ?: localProperties.getProperty("megingiard.keystore.alias")

            val candidateFile = when {
                !keystoreFileProp.isNullOrBlank() -> rootProject.file(keystoreFileProp)
                rootProject.file("bdsa.jks").exists() -> rootProject.file("bdsa.jks")
                else -> rootProject.file("megingiard.jks")
            }

            if (!keyPasswordProp.isNullOrBlank() && !storePasswordProp.isNullOrBlank() && !aliasProp.isNullOrBlank()) {
                storeFile = candidateFile
                storePassword = storePasswordProp
                keyAlias = aliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        val releaseSigningConfig = signingConfigs.findByName("release")
        val hasValidReleaseKeystore =
            releaseSigningConfig != null && releaseSigningConfig.storeFile?.exists() == true

        debug {
            if (hasValidReleaseKeystore) {
                signingConfig = releaseSigningConfig
            }
        }
        release {
            signingConfig = if (hasValidReleaseKeystore) {
                releaseSigningConfig
            } else {
                logger.warn("WARNING: Release keystore not configured or missing; falling back to debug signing config.")
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
}
