plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.ai"
}

dependencies {
    implementation(project(":core:data"))
}
