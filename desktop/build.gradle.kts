import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Windows desktop version (M17). Compose Multiplatform for the desktop JVM, sharing :core with the Android app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Versions come from the release workflow (-PversionName=0.17.0); local builds are 0.0.1.
val appVersionName = providers.gradleProperty("versionName").orElse("0.0.1").get()

sourceSets {
    main {
        // Lesson/ear content and the app's fonts are shared with Android, not copied.
        resources.srcDir("../content")
        resources.srcDir("../app/src/main/res/font")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jflac.codec)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.id.mimikopi.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Mimikopi"
            packageVersion = appVersionName
            description = "Mimikopi piano practice"
            vendor = "Mimikopi"
            // javax.sound (MIDI, audio) and java.net.http (sample pack download) live outside java.base.
            modules("java.desktop", "java.net.http", "jdk.crypto.ec")
            windows {
                menu = true
                menuGroup = "Mimikopi"
                shortcut = true
                perUserInstall = true
                // Fixed so later versions upgrade the same installation instead of installing beside it.
                upgradeUuid = "7d1f6c1e-2b2a-4a63-9c8f-1d6f0e9a5b41"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}
