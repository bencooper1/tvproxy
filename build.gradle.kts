// TVProxy root build file.
// Plugins are declared here and applied in :app (see architecture.md §3).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source = files(
        "app/src/main/java",
        "app/src/test/java",
        "app/src/androidTest/java"
    )
    warningsAsErrors = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        xml.required.set(false)
        md.required.set(false)
    }
}
