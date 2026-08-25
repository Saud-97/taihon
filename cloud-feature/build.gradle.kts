import mihon.gradle.Config

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "taihon.feature.cloud"

    sourceSets {
        getByName("main") {
            if (Config.includeCloud) {
                kotlin.directories.add("src/cloud/kotlin")
            } else {
                kotlin.directories.add("src/noop/kotlin")
            }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.i18n)
    implementation(libs.injekt)

    if (Config.includeCloud) {
        implementation(libs.androidx.credentials)
        implementation(libs.androidx.credentials.play.services.auth)
        implementation(libs.googleid)
        implementation(libs.google.auth)
        implementation(libs.google.api.client.android)
        implementation(libs.google.api.client.gson)
        implementation(libs.google.api.services.drive)
    }
}
