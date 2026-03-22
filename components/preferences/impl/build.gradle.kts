import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":components:preferences:api"))
            implementation(project(":components:shared:api"))
            implementation(project(":components:game-core:api"))
            implementation(libs.kotlininject.runtime)
            implementation(libs.kubriko.engine)
            implementation(libs.kubriko.plugin.persistence)
        }
    }
}

dependencies {
    kspCommonMainMetadata(libs.kotlininject.compiler)
    add("kspJvm", libs.kotlininject.compiler)
    add("kspAndroid", libs.kotlininject.compiler)
}

android {
    namespace = "jez.lastfleetprotocol.components.preferences"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
