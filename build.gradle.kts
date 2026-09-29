// Top-level build file. Plugins are declared here with `apply false` so each
// module opts in via the version catalog. House style: manual DI, no Hilt/Koin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// The i18n ratchet. See gradle/i18n/verify-i18n.gradle.kts for what it checks
// and why Android Lint's HardcodedText cannot do this job.
apply(from = "gradle/i18n/verify-i18n.gradle.kts")

// One task that runs every module's unit tests. See the script for why the
// obvious task list did not: ci.yml's three variant tasks silently skipped
// :core:model, the most-tested module in the repo.
apply(from = "gradle/verification/unit-tests.gradle.kts")
