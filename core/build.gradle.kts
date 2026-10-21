plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure JVM module: no Android imports allowed (kept KMP-convertible).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

// Tests read the editable practice library directly; content changes must invalidate cached results.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("content"))
        .withPropertyName("practiceContent")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
