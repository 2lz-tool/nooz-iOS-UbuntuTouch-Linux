pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Working name only. Final name is RESERVED (see LICENSE.RESERVED / STATE.md).
rootProject.name = "riverwip"

include(":app")
include(":core:model")
include(":core:data")
include(":core:inference-api")
include(":core:inference")
include(":core:design")
include(":feature:sources")
include(":feature:reader")
include(":feature:river")
include(":feature:lens")
