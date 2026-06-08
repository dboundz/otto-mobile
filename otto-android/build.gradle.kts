// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// External exFAT/APFS volumes can write AppleDouble `._*` sidecars into build outputs;
// Gradle then fails to delete or parse those paths. Strip them before each :app task.
subprojects {
    if (name == "app") {
        tasks.configureEach {
            doFirst {
                val buildDir = layout.buildDirectory.get().asFile
                if (buildDir.exists()) {
                    buildDir.walkTopDown()
                        .filter { it.isFile && it.name.startsWith("._") }
                        .forEach { it.delete() }
                }
            }
        }
    }
}