plugins {
    `kotlin-dsl`
}

group = "com.vaultly.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "vaultly.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "vaultly.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "vaultly.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmDomain") {
            id = "vaultly.jvm.domain"
            implementationClass = "JvmDomainConventionPlugin"
        }
    }
}
