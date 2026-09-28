import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Kotlin Multiplatform data layer: Room (KMP) + DataStore (KMP) + okio for files.
// Everything platform-specific hangs off `DataPlatform` (see DataPlatform.kt): where
// files live, how the DataStore/database are opened, how assets are read. The
// Android app keeps its exact behaviour -- same database file, same DataStore
// files, same HttpURLConnection transport, WorkManager for scheduling.
//
// `-Pnooz.skipAndroid` drops the Android target so the JVM and native targets can
// be built and tested on a machine without an Android SDK. CI never sets it.
val skipAndroid = providers.gradleProperty("nooz.skipAndroid").isPresent

if (!skipAndroid) {
    pluginManager.apply("com.android.library")
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        sourceSets.getByName("main").assets.srcDir("src/commonAssets")
        namespace = "${property("riverwip.packageBase")}.data"
        compileSdk = 35
        defaultConfig {
            minSdk = 31
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    linuxX64()
    linuxArm64()
    iosArm64()
    iosSimulatorArm64()

    // Custom source sets (jvmCommonMain) switch the default hierarchy off; turn it back on so nativeMain exists.
    applyDefaultHierarchyTemplate()

    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    sourceSets {
        // One copy of the bundled text assets (common_words.txt, ai_catalogue_models.json): Android
        // reads them from assets/, the desktop JVM from the classpath.
        jvmMain { resources.srcDir("src/commonAssets") }
        // Android and desktop JVM share the HttpURLConnection transport.
        val jvmCommonMain = create("jvmCommonMain") { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmCommonMain)
        if (!skipAndroid) getByName("androidMain").dependsOn(jvmCommonMain)

        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
            api(libs.androidx.sqlite)
            implementation(libs.ksoup)
        }
        // Tests that need a real SQLite file (Room, the translation packs) run on the JVM and native
        // targets, which can open one directly. Android would need Robolectric + a Context for the same.
        val sqliteTest = create("sqliteTest") { dependsOn(commonTest.get()) }
        jvmTest.get().dependsOn(sqliteTest)
        getByName("nativeTest").dependsOn(sqliteTest)

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        if (!skipAndroid) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.androidx.sqlite.framework)
            }
            getByName("androidUnitTest").dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.junit)
            }
        }
    }
}

room {
    // Schemas are checked in (D37): they are what let the migration tests open a
    // real older database instead of asserting a migration is right by reading it.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val compiler = libs.androidx.room.compiler
    if (!skipAndroid) add("kspAndroid", compiler)
    add("kspJvm", compiler)
    add("kspLinuxX64", compiler)
    add("kspLinuxArm64", compiler)
    add("kspIosArm64", compiler)
    add("kspIosSimulatorArm64", compiler)
}
