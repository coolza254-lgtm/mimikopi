// Mimikopi for Windows: a separate Gradle build so it never needs the Android SDK. It compiles the very same
// ../core module as the Android app (one copy of the rules) and reads the same version catalog.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Compose Multiplatform for desktop depends on a few JVM builds of AndroidX libraries published only here.
        google {
            content {
                includeGroupByRegex("androidx.*")
            }
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "mimikopi-desktop"

include(":core")
project(":core").projectDir = file("../core")

// platform: storage, audio, MIDI, practice sessions; plain JVM, so it builds and tests anywhere.
// app: the Compose Desktop windows and the Windows installer.
include(":platform", ":app")
