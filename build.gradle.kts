// TVProxy root build file.
// Plugins are declared here and applied in :app (see architecture.md §3).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        "app/src/main/java",
        "app/src/test/java",
        "app/src/androidTest/java"
    )
}

// Zero-tolerance gate: `build.maxIssues: 0` in config/detekt/detekt.yml fails the
// detekt task on any finding, so no extra task property is needed here.
// (detekt 1.23.x exposes neither extension- nor task-level `warningsAsErrors`.)
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        xml.required.set(false)
        md.required.set(false)
    }
}
