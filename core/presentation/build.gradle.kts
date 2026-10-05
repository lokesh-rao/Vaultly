plugins {
    alias(libs.plugins.vaultly.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vaultly.core.presentation"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.androidx.compose)
}
