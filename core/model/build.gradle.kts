import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Kotlin Multiplatform, and still no Android, no Compose. This module is the
// app's testable domain core: data types, taxonomy, and the analytical logic
// that must be unit-verified (river decomposition, dedup simhash, classifier
// rules, the lens fidelity guard, the CVD palette math). Keeping it free of
// platform APIs means one implementation serves every port:
//
//   jvm            Android app + Linux desktop (Compose Desktop)
//   linuxX64/Arm64 Ubuntu Touch (Qt/QML shell over this core) + native Linux
//   iosArm64/Sim   iOS
//
// Anything platform-specific lives behind an interface in the consuming module,
// never here. Targets the host cannot build (iOS on Linux) are skipped by the
// Kotlin plugin, so this one file works on every CI runner.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    linuxX64()
    linuxArm64()
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Only the JVM-side parity tests (which use the JDK as an oracle for
            // the portable replacements) need this.
            implementation(libs.junit)
        }
    }
}
