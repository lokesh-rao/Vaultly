plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
}
