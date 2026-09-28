// unitTests -- one task that actually runs every module's unit tests.
//
// WHY THIS EXISTS
// ci.yml asked for `testDebugUnitTest testFossDebugUnitTest testFullDebugUnitTest`,
// which reads like "all of them" and is not. Those are Android variant tasks.
// `:core:model` is a plain `kotlin.jvm` module, so its task is `test` — and it
// is the most-tested module in the repo. Twenty-seven test files (Simhash,
// Dedup, ArticleSearch, the feed parser, OPML, the classifier) had never been
// executed by CI, and nothing said so: the build went green because every task
// that was asked for passed.
//
// A hand-maintained list of task names has the same failure mode the moment
// someone adds a module, so this discovers them instead — and fails loudly if a
// module has test sources it cannot find a task for.

val unitTests = tasks.register("unitTests") {
    group = "verification"
    description = "Runs every module's unit tests, whatever each module's task happens to be called."
}

// Modules deliberately left out of the default run, with the reason. Empty
// today; kept so that an exclusion has to be written down rather than happening
// by accident, which is exactly how :core:model went missing.
val skippedModules = setOf<String>()

// projectsEvaluated, not each project's own afterEvaluate: AGP registers its
// variant tasks from inside its own afterEvaluate hook, so a callback added
// here runs first and sees an Android module as having no test task at all —
// which the check below would then report as an error. Waiting until every
// project is evaluated is the difference between "no such task" and "not yet".
gradle.projectsEvaluated {
    for (project in rootProject.subprojects) {
        if (project.path in skippedModules) continue

        val names = project.tasks.names
        // Android variants first: a flavored module has no bare
        // `testDebugUnitTest`, only `testFossDebugUnitTest` and friends.
        val variantTests = names.filter { it.matches(Regex("^test[A-Za-z]*DebugUnitTest$")) }
        val chosen = when {
            variantTests.isNotEmpty() -> variantTests
            "test" in names -> listOf("test")
            // Kotlin Multiplatform modules (:core:model): the JVM target runs commonTest + jvmTest.
            // Their native targets (linuxX64Test, iOS) need a matching host and run in their own CI jobs.
            "jvmTest" in names -> listOf("jvmTest")
            else -> emptyList()
        }

        val hasTestSources = listOf("src/test", "src/commonTest", "src/jvmTest").map { project.file(it) }.any { dir ->
            dir.exists() && dir.walkTopDown().any { it.isFile && (it.extension == "kt" || it.extension == "java") }
        }

        if (chosen.isEmpty() && hasTestSources) {
            throw GradleException(
                "${project.path} has test sources but no unit-test task this build knows how to " +
                    "run. Add it to `skippedModules` in gradle/verification/unit-tests.gradle.kts " +
                    "with a reason, or teach that file the task's name — silently not running " +
                    "tests is the bug this task exists to prevent.",
            )
        }

        unitTests.configure { dependsOn(chosen.map { "${project.path}:$it" }) }
    }
}
