import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The Nooz app itself, as UI: the shell (NoozApp), settings, onboarding, model choice, and the
// adaptive layouts that decide -- from the window's width, on every platform -- whether the Stand
// is one pane or list + reader side by side. Hosts supply an `AppServices`: `:app` on Android,
// `:desktop` on Linux.
val skipAndroid = providers.gradleProperty("nooz.skipAndroid").isPresent

if (!skipAndroid) {
    pluginManager.apply("com.android.library")
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "${property("riverwip.packageBase")}.shared"
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
            api(project(":core:data"))
            api(project(":core:inference-api"))
            api(project(":feature:sources"))
            api(project(":feature:reader"))
            api(project(":feature:river"))
            api(project(":feature:lens"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jb.lifecycle.viewmodel)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

// Compose's resource lookup asks AWT for the screen density, which throws HeadlessException in a
// headless JVM. Tests run headful; CI supplies a display via xvfb-run.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "false")
}
