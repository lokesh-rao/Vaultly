plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.notes"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
}
