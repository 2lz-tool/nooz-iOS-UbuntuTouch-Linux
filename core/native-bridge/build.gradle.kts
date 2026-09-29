plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The Nooz core as a plain C library, for the Ubuntu Touch app (Qt/QML front end) -- and any other
// native host. Kotlin/Native compiles :core:model and :core:data (feeds, database, full-text
// extraction, HTTP over the system libcurl) into libnooz_core.so plus a C header; the front end
// talks to it through a handful of JSON-in / JSON-out functions (see NoozApi.kt).
//
// linuxArm64 is the phone; linuxX64 is the desktop build the tests and the QML harness run on.
kotlin {
    linuxX64 { binaries { sharedLib { baseName = "nooz_core" } } }
    linuxArm64 { binaries { sharedLib { baseName = "nooz_core" } } }

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
        }
    }
}
