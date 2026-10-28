// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = java.util.Properties()

if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

extra["MAPS_API_KEY"] = secrets.getProperty("MAPS_API_KEY", "")
