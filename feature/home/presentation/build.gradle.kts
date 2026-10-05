plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.home"
}

dependencies {
    implementation(project(":core:data"))
}
