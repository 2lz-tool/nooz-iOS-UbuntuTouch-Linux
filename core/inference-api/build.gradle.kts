import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The portable half of the reader-intelligence stack: the provider contracts, the router,
// prompt templates, BYOK (any OpenAI-compatible endpoint) and model-download utilities.
// The on-device providers that need Android (llama.cpp JNI, ONNX/Kokoro, ML Kit, Urbana)
// stay in :core:inference, which depends on this module.
val skipAndroid = providers.gradleProperty("nooz.skipAndroid").isPresent

if (!skipAndroid) {
    pluginManager.apply("com.android.library")
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "${property("riverwip.packageBase")}.inference.api"
        compileSdk = 35
        defaultConfig { minSdk = 31 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions { unitTests.isReturnDefaultValues = true }
    }
}

kotlin {
    if (!skipAndroid) {
        androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    linuxX64()
    linuxArm64()
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:data"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
