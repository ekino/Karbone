plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.dokka)
  alias(libs.plugins.kotest)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  alias(libs.plugins.gradle.maven.publish.plugin)
}

group = "com.ekino.oss"

// Release builds take their version from the v* tag (v1.2.3 -> 1.2.3); everything else uses the
// localVersion snapshot
// from gradle.properties, on CI as well as locally, so snapshots are named predictably
// (0.1.5-SNAPSHOT, not <sha>-SNAPSHOT).
version =
  System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_ACTIONS") != null && it.startsWith("v") }
    ?.removePrefix("v") ?: project.findProperty("localVersion") as String? ?: "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }

dependencies {
  api(libs.arrow.core)
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.logging)

  testImplementation(libs.bundles.kotest.extended)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.testcontainers)
}

kotlin {
  explicitApi()
  compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict", "-jvm-default=enable") }
}

// Local secrets for integration tests (e.g. CARBONE_TEST_API_KEY) come from a gitignored .env; see
// .env.example.
val dotEnv: Map<String, String> =
  rootProject
    .file(".env")
    .takeIf { it.isFile }
    ?.readLines()
    .orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
    .associate { line ->
      line.substringBefore("=").trim() to line.substringAfter("=").trim().removeSurrounding("\"")
    }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  // Real environment wins over .env so CI secrets are never overridden.
  dotEnv.filterKeys { System.getenv(it) == null }.forEach { (k, v) -> environment(k, v) }
}

// KarboneConfig reads the SDK version from the jar manifest (User-Agent header).
tasks.jar {
  manifest {
    attributes(
      "Implementation-Title" to "Karbone",
      "Implementation-Version" to project.version.toString(),
      "Implementation-Vendor" to "ekino",
    )
  }
}

// Required by the io.kotest plugin (no default in 6.2.x): enables the `kotest` / `jvmKotest` tasks.
kotest { customGradleTask.set(true) }

tasks.named("check") { dependsOn(tasks.named("detektMain")) }

detekt {
  buildUponDefaultConfig.set(true)
  allRules.set(false)
  config.setFrom(rootProject.file("detekt.yml"))
}

spotless {
  val licenseHeaderText =
    """
    |/*
    | * Copyright (c) 2026 ekino (https://www.ekino.com/)
    | */
    """
      .trimMargin()

  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**", ".gradle-home/**")
    licenseHeader(licenseHeaderText)
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle().configure { it.setRemoveUnusedImports(true) }
    trimTrailingWhitespace()
    endWithNewline()
  }

  format("markdown") {
    target("**/*.md")
    targetExclude("**/build/**", ".gradle-home/**", "ClaudeContexts/**")
    prettier().configFile(rootProject.file(".prettierrc.json"))
  }

  yaml {
    target("**/*.yml", "**/*.yaml")
    targetExclude("**/build/**", ".gradle-home/**", "ClaudeContexts/**")
    prettier().configFile(rootProject.file(".prettierrc.json"))
  }
}

mavenPublishing {
  coordinates(
    groupId = project.group.toString(),
    artifactId = "karbone",
    version = project.version.toString(),
  )

  publishToMavenCentral(automaticRelease = true)
  // Signing keys are only available in the GitHub release workflow; GitLab (internal review)
  // publishes unsigned.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

  pom {
    name.set("Karbone")
    description.set("A modern Kotlin SDK for the Carbone.io document generation API")
    url.set("https://github.com/ekino/Karbone")

    licenses {
      license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
      }
    }

    developers {
      developer {
        id.set("Benoit.Havret")
        name.set("Benoît Havret")
        email.set("benoit.havret@ekino.com")
        organization.set("ekino")
        organizationUrl.set("https://github.com/ekino")
      }
    }

    scm {
      connection.set("scm:git:git://github.com/ekino/Karbone.git")
      developerConnection.set("scm:git:ssh://github.com/ekino/Karbone.git")
      url.set("https://github.com/ekino/Karbone")
    }
  }
}
