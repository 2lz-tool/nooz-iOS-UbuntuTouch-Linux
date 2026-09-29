import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Compose Multiplatform feature module: Android + desktop JVM today (iOS joins with its shell).
// `-Pnooz.skipAndroid` drops Android for machines without an Android SDK; CI never sets it.
val skipAndroid = providers.gradleProperty("nooz.skipAndroid").isPresent

if (!skipAndroid) {
    pluginManager.apply("com.android.library")
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "${property("riverwip.packageBase")}.feature.lens"
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
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:design"))
            implementation(project(":core:data"))
            implementation(project(":core:inference-api"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jb.lifecycle.viewmodel)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            
        }
        if (!skipAndroid) {
            getByName("androidMain").dependencies {
                
            }
        }
    }
}
