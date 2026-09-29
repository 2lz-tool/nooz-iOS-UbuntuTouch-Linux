import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The design system: theme, tokens, type, shared controls. Compose Multiplatform, so
// the same composables render on Android and on the Linux desktop (and later iOS /
// Ubuntu Touch shells). Strings, fonts and images are Compose resources (see
// src/commonMain/composeResources), in the same values-b+<tag> layout as before.
//
// `-Pnooz.skipAndroid` drops the Android target for machines without an Android SDK.
val skipAndroid = providers.gradleProperty("nooz.skipAndroid").isPresent

if (!skipAndroid) {
    pluginManager.apply("com.android.library")
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "${property("riverwip.packageBase")}.design"
        compileSdk = 35
        defaultConfig {
            minSdk = 31
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests.isReturnDefaultValues = true
            unitTests.isIncludeAndroidResources = true
        }
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
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        if (!skipAndroid) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
            }
            getByName("androidUnitTest").dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.compose.ui.test.junit4)
            }
        }
    }
}

// Compose's resource lookup asks AWT for the screen density, which throws HeadlessException in a
// headless JVM (Gradle's test default). Tests therefore run headful; CI supplies a display via xvfb-run.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "false")
}

compose.resources {
    // `import xyz.mdhv.riverwip.design.res.*` brings Res and every string/font/drawable accessor into scope.
    packageOfResClass = "xyz.mdhv.riverwip.design.res"
    publicResClass = true
    generateResClass = always
}
