plugins {
    alias(libs.plugins.android.library)
}

// Native audio engine (C++ / Oboe) behind the Kotlin PianoEngine interface.
android {
    namespace = "com.id.mimikopi.audio"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        externalNativeBuild {
            cmake {
                // Oboe's prefab package requires the shared C++ runtime.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.oboe)
}
