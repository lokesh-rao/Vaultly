plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.vault"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material.icons)
    implementation("androidx.compose.foundation:foundation")
}
