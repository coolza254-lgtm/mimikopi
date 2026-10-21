plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        // Lessons, pieces, exercises and the learning config are the Android app's files, bundled as they are.
        resources.srcDir("../../content")
    }
}

dependencies {
    api(project(":core"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The native audio library (desktop/native) is built by CMake; tests that need it look for it here.
tasks.withType<Test>().configureEach {
    systemProperty("mimikopi.native.dir", rootProject.layout.projectDirectory.dir("native/build").asFile.absolutePath)
}
