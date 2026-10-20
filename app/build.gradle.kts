plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release builds get their version from the git tag (see .github/workflows/release.yml).
val appVersionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
val appVersionName = providers.gradleProperty("versionName").orElse("0.0.0-dev").get()

// Signing material comes only from the environment (GitHub Actions secrets), never from git.
val keystorePath: String? = System.getenv("MIMIKOPI_KEYSTORE_FILE")

android {
    namespace = "com.id.mimikopi"
    // Current AndroidX (Compose 1.12, Navigation 2.10) requires compiling against API 37.
    // targetSdk stays 36: runtime behaviour is opted in separately.
    compileSdk = 37

    defaultConfig {
        // Frozen in M0. Changing it breaks updates over installed copies.
        applicationId = "com.id.mimikopi"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MIMIKOPI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIMIKOPI_KEY_ALIAS")
                keyPassword = System.getenv("MIMIKOPI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a CI debug build sit next to the real (release-signed) app on the phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Lesson and config content (content/) ships as assets: editable files, not code.
    sourceSets {
        getByName("main") {
            assets.directories += "../content"
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

    lint {
        // Thai-only UI for now; English strings are added later.
        disable += "MissingTranslation"
        // targetSdk follows Google's current requirement (36), not the newest SDK; see docs/M0-PLAN.md.
        disable += "OldTargetApi"
        abortOnError = true
        warningsAsErrors = false
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    testImplementation(libs.kotlin.test)
    implementation(project(":core"))
    implementation(project(":audio"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
