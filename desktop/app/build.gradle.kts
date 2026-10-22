import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

// Builds pass -PversionName, the phone's version (0.30.0). MSI needs MAJOR.MINOR.BUILD with MAJOR > 0 and only ever
// upgrades to a higher one: the package uses 2.x.y for 0.x.y, above the 1.x.y of the earlier test builds (0.31.<run>).
val appVersionName = providers.gradleProperty("versionName").orElse("0.30.0").get()

fun packageVersionFor(v: String): String {
    val parts = v.split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    return "${parts[0] + 2}.${parts[1]}.${parts[2]}"
}

dependencies {
    implementation(project(":platform"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(compose.desktop.uiTestJUnit4)
}

sourceSets {
    main {
        // The music font is the Android app's file.
        resources.srcDir(layout.buildDirectory.dir("generated/sharedResources"))
    }
}

val copySharedResources = tasks.register<Copy>("copySharedResources") {
    from("../../app/src/main/res/font/bravura.otf")
    into(layout.buildDirectory.dir("generated/sharedResources/fonts"))
}
tasks.named("processResources") { dependsOn(copySharedResources) }

// Tests that open the piano use the CMake-built library (desktop/native/build).
tasks.withType<Test>().configureEach {
    systemProperty("mimikopi.native.dir", rootProject.layout.projectDirectory.dir("native/build").asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "com.id.mimikopi.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dmimikopi.version=$appVersionName")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Mimikopi"
            packageVersion = packageVersionFor(appVersionName)
            // ASCII only: WiX writes these into the MSI with a Windows codepage.
            description = "Mimikopi piano practice"
            vendor = "Mimikopi"
            modules("java.desktop", "java.sql", "java.net.http", "jdk.unsupported")
            // The native audio library (desktop/native, built by CMake) ships beside the jars.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("../native/dist"))
            windows {
                menu = true
                menuGroup = "Mimikopi"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Fixed so later versions upgrade the same installation instead of installing beside it.
                upgradeUuid = "3f2c9a64-5d0e-4b8f-9a71-6c2e1f08d4b3"
            }
        }
    }
}
