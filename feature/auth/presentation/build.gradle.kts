plugins {
    alias(libs.plugins.vaultly.android.feature)
}

android {
    namespace = "com.vaultly.feature.auth"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:security"))
    implementation(libs.androidx.biometric)
}
