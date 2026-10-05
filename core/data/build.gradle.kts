plugins {
    alias(libs.plugins.vaultly.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vaultly.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    api(project(":core:security"))
    api(project(":core:database"))
    api(project(":core:domain"))
    implementation(libs.koin.android)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kermit)
    implementation(libs.androidx.lifecycle.process)
}
