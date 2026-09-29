plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "${property("riverwip.packageBase")}.inference"
    compileSdk = 35

    // Pinned rather than left to whatever's on the machine/runner: this is
    // the first native (NDK/CMake) build in the project (Nooz Cast's
    // onnxruntime-android is a prebuilt AAR, no compilation needed), and an
    // explicit, known-good version keeps it reproducible across local
    // machines and CI. AGP/sdkmanager auto-downloads it on demand.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 31
        // Matches app/build.gradle.kts's own restriction (see that file's
        // comment) — every ABI llama.cpp would otherwise also get built for
        // here (armeabi-v7a, x86, x86_64) is one the app never ships anyway.
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }
    // The llama.cpp build this pulls in (see src/main/cpp/CMakeLists.txt) is
    // real C/C++ source compiled from scratch — unlike every other module
    // here, a clean build of this one takes several minutes.
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // `full` may add ML Kit GenAI; `foss` must stay free of proprietary deps.
    // The llama.cpp runtime below is MIT-licensed and open source, so it's
    // built into both flavors, unlike ML Kit.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") { dimension = "distribution" }
        create("full") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:inference-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Kotlin 2.3 removed `kotlinOptions { jvmTarget = ... }`; this is the compilerOptions equivalent.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
