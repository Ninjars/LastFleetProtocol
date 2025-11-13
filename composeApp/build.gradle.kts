import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_19)
        }
    }

//    listOf(
//        iosX64(),
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach {
//        it.binaries.framework {
//            baseName = "shared"
//        }
//    }


    jvm()
    jvmToolchain(19)

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
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

            // Kubriko Engine Dependencies
            // Engine
            implementation(libs.kubriko.engine)

            // Plugins
            implementation(libs.kubriko.plugin.audio)
            implementation(libs.kubriko.plugin.collision)
            implementation(libs.kubriko.plugin.keyboard)
            implementation(libs.kubriko.plugin.particles)
            implementation(libs.kubriko.plugin.persistence)
            implementation(libs.kubriko.plugin.pointer)
            implementation(libs.kubriko.plugin.physics)
            implementation(libs.kubriko.plugin.shaders)
            implementation(libs.kubriko.plugin.sprites)

            // Tools
            implementation(libs.kubriko.tool.logger)
            implementation(libs.kubriko.tool.ui.components)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

dependencies {
    kspCommonMainMetadata(libs.kotlininject.compiler)
    add("kspJvm", libs.kotlininject.compiler)
    add("kspAndroid", libs.kotlininject.compiler)
//    add("kspIosX64", libs.kotlininject.compiler)
//    add("kspIosArm64", libs.kotlininject.compiler)
//    add("kspIosSimulatorArm64", libs.kotlininject.compiler)
}

android {
    namespace = "jez.lastfleetprotocol"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "jez.lastfleetprotocol"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "jez.lastfleetprotocol.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "jez.lastfleetprotocol"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<com.google.devtools.ksp.gradle.KspAATask>().configureEach {
    // This ensures that the resource generation task runs before KSP
    dependsOn(
        // Android
        "generateActualResourceCollectorsForAndroidMain",
        "generateResourceAccessorsForAndroidMain",
        "generateActualResourceCollectorsForAndroidMain",
        "generateComposeResClass",
        "generateResourceAccessorsForCommonMain",
        "generateExpectResourceCollectorsForCommonMain",
        "generateResourceAccessorsForAndroidDebug",
        // iOS
//        "generateResourceAccessorsForIosArm64Main",
//        "generateActualResourceCollectorsForIosArm64Main",
//        "generateResourceAccessorsForIosMain",
//        "generateResourceAccessorsForAppleMain",
//        "generateResourceAccessorsForNativeMain",
    )
}
