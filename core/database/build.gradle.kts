plugins {
    alias(libs.plugins.vaultly.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vaultly.core.database"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
