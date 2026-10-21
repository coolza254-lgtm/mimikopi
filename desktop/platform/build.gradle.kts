plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

// Lessons, pieces, exercises and the learning config are the Android app's files, bundled as they are, plus a list
// of them (a folder inside a jar cannot be listed).
val contentIndex = tasks.register("contentIndex") {
    val content = layout.projectDirectory.dir("../../content")
    val out = layout.buildDirectory.dir("generated/contentIndex")
    inputs.dir(content).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(out)
    doLast {
        val base = content.asFile
        val files = base.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }
            .map { it.relativeTo(base).invariantSeparatorsPath }.sorted().toList()
        val dir = out.get().asFile.resolve("content")
        dir.mkdirs()
        dir.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n"))
    }
}

sourceSets {
    main {
        resources.srcDir("../../content")
        resources.srcDir(contentIndex)
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
