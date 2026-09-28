import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "${property("riverwip.packageBase")}"
    compileSdk = 35

    // Must match :core:inference's own ndkVersion. That module builds the
    // llama.cpp native libraries, but the *packaging* of them happens here,
    // and two of AGP's release steps need an NDK resolvable in THIS module to
    // run at all: stripping debug symbols out of every packaged .so, and
    // extracting them into the bundle's native-debug-symbols metadata for
    // Play. With no ndkVersion declared here, AGP couldn't find a strip tool,
    // logged a warning, and packaged the libraries exactly as built —
    // shipping ~156MB of unstripped native code (libllama-common.so alone was
    // 78MB, against 4MB stripped) and no symbol file for Play to symbolicate
    // native crashes with. Found by unzipping a real release .aab and running
    // `file` over base/lib/arm64-v8a/*.so: everything CMake-built came back
    // "not stripped", while the prebuilt onnxruntime .so from its AAR was
    // already stripped by its own publisher.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        // Final applicationId — dev.asystemofcells.nooz, as registered in Play
        // Console. A separate property from riverwip.packageBase (used for
        // `namespace` above and every other module): that one has to keep
        // matching the actual xyz.mdhv.riverwip Kotlin package declarations
        // across the source tree, or generated R classes stop resolving.
        // The `full` flavor below is deliberately left with no
        // applicationIdSuffix so bundleFullRelease's AAB matches this exactly.
        applicationId = "${property("riverwip.applicationId")}"
        minSdk = 31
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // onnxruntime-android (Nooz Cast) bundles a real native libonnxruntime.so
        // per ABI — confirmed by inspecting the .aar: arm64-v8a ~27MB,
        // armeabi-v7a ~20MB, x86 ~34MB, x86_64 ~34MB. That native weight (which R8
        // can't shrink — it only touches bytecode + resources) is what makes the
        // bundle large, so we ship arm64-v8a only: every phone/tablet from ~2017
        // on is 64-bit, 32-bit-only armeabi-v7a is a vanishing sliver, and x86/
        // x86_64 only matter for emulators. Dropping the rest cuts ~88MB of dead
        // native libs from every build (the release .aab and the test APKs alike).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Two flavors: `foss` (F-Droid-eligible; zero proprietary/Play deps) and
    // `full` (may add ML Kit GenAI). All features work in foss.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
        }
        create("full") {
            dimension = "distribution"
        }
    }

    // A fixed debug keystore, committed to the repo (owner: "every time I get
    // a new apk, I get 'app not installed'"). CI runs on ephemeral runners
    // with no persisted `~/.android/debug.keystore`, so without this AGP's
    // implicit debug signingConfig generates a brand-new random key on every
    // run — a real Android install refuses to update an app whose new APK
    // isn't signed with the same key as the one already installed, so every
    // fresh CI build conflicted with whichever build came before it. This
    // keystore is debug-only (never used for the release signingConfig below,
    // never uploaded anywhere) — standard "androiddebugkey"/"android"
    // credentials, safe to commit.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing, deliberately NOT committed to the repo (unlike the
        // debug keystore above) — this key controls the app's identity on the
        // Play Store, so it lives only as a local `keystore.properties` (see
        // `keystore.properties.example`, gitignored) or, in CI, as decoded
        // GitHub Actions secrets passed through these env vars (see
        // `.github/workflows/release.yml`). Left unset — not a build failure —
        // when neither source is present, since only the release-bundling task
        // actually needs it; every other CI job (debug assemble, unit tests)
        // never touches this config.
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }

            val path = props.getProperty("storeFile") ?: System.getenv("RELEASE_KEYSTORE_PATH")
            if (path != null) storeFile = file(path)
            storePassword = props.getProperty("storePassword") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = props.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // App optimization (owner's #15): R8 code shrinking + obfuscation and
            // resource shrinking, so the release download is as light as we can
            // make it. R8 runs in full mode (AGP 8 default). Debug stays fast to
            // build and is the only variant the routine CI workflow assembles —
            // release only builds on-demand, via release.yml.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            // The bundle ships native code (libonnxruntime.so from Nooz Cast), so
            // Play asks for a native-debug-symbols file to symbolicate native
            // crashes/ANRs. AGP extracts it into the .aab's BUNDLE-METADATA — it is
            // upload-only metadata that Play strips before distribution, so it adds
            // nothing to the on-device download. SYMBOL_TABLE gives function names
            // in native stack traces at a fraction of FULL's size (FULL adds file/
            // line + frame info, which the prebuilt onnxruntime .so is stripped of
            // anyway); it's the right trade for a third-party native lib.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Per-device downloads from the App Bundle stay small — one language, one
    // density, one ABI per install instead of the universal APK.
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:inference"))
    implementation(project(":feature:sources"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:river"))
    implementation(project(":feature:lens"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Kotlin 2.3 removed `kotlinOptions { jvmTarget = ... }`; this is the compilerOptions equivalent.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
