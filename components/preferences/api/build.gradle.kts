import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kubriko.engine)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "jez.lastfleetprotocol.components.preferences"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
