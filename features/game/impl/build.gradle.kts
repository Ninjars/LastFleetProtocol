import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:game:api"))
            implementation(project(":components:shared:api"))
            implementation(project(":components:design"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.jetpack.navigation)
            implementation(libs.kotlinx.json)
            implementation(libs.kotlininject.runtime)
            implementation(libs.backhandler)
            implementation(libs.kubriko.engine)
            implementation(libs.kubriko.plugin.audio)
            implementation(libs.kubriko.plugin.collision)
            implementation(libs.kubriko.plugin.keyboard)
            implementation(libs.kubriko.plugin.particles)
            implementation(libs.kubriko.plugin.persistence)
            implementation(libs.kubriko.plugin.pointer)
            implementation(libs.kubriko.plugin.physics)
            implementation(libs.kubriko.plugin.shaders)
            implementation(libs.kubriko.plugin.sprites)
            implementation(libs.kubriko.tool.logger)
            implementation(libs.kubriko.tool.ui.components)
        }
        androidMain.dependencies {
            implementation(compose.preview)
        }
    }
}

dependencies {
    kspCommonMainMetadata(libs.kotlininject.compiler)
    add("kspJvm", libs.kotlininject.compiler)
    add("kspAndroid", libs.kotlininject.compiler)
}

android {
    namespace = "jez.lastfleetprotocol.features.game"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
