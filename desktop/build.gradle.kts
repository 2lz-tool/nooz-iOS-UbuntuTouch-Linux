import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The Linux desktop app: a window around the same NoozApp the Android app shows, with desktop
// implementations of the platform seams (XDG directories, native file dialogs, a refresh timer
// instead of WorkManager). Packaged by CI as a .deb and a portable app image.

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:inference-api"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // Room's KMP runtime on the desktop JVM.
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.jb.lifecycle.runtime.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

kotlin { jvmToolchain(17) }

tasks.withType<Test>().configureEach {
    // Compose's resource lookup asks AWT for the screen density; tests run headful under xvfb-run in CI.
    systemProperty("java.awt.headless", "false")
}

compose.desktop {
    application {
        mainClass = "xyz.mdhv.riverwip.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "nooz"
            packageVersion = "0.3.1"
            description = "Read what's there, and notice what isn't."
            vendor = "mbaliga"
            licenseFile.set(rootProject.file("LICENSE"))
            // jlink drops modules it cannot see being used; these are loaded reflectively by
            // Room's bundled SQLite driver, the JDBC-free logging fallback and the desktop dialogs.
            modules("java.sql", "jdk.unsupported")
            linux {
                iconFile.set(project.file("src/main/resources/nooz.png"))
                menuGroup = "Office"
                appCategory = "Network"
                debMaintainer = "mbaliga"
            }
        }
    }
}
