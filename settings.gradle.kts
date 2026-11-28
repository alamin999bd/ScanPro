pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                // Resolve the KSP implementation directly instead of relying on
                // the Gradle Plugin Portal marker artifact.
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only the offline OCR library (Tesseract4Android) is published here, not on Maven Central.
        maven("https://jitpack.io") {
            content { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "ScanPro"
include(":app")
