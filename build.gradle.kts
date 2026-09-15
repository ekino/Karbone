plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotest)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  alias(libs.plugins.gradle.maven.publish.plugin)
}

group = "com.ekino.oss"

version =
  when {
    // CI environment - GitHub Actions
    System.getenv("GITHUB_ACTIONS") != null -> {
      // Tag-based releases: only v* tags (v1.0.0 -> 1.0.0)
      val tag = System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }
      tag?.removePrefix("v")
        ?: runCatching {
            val gitDescribe =
              providers
                .exec {
                  commandLine("git", "describe", "--tags", "--always", "--dirty", "--abbrev=7")
                }
                .standardOutput
                .asText
                .get()
                .trim()
            "${gitDescribe.removePrefix("v")}-SNAPSHOT"
          }
          .getOrElse {
            val sha = System.getenv("GITHUB_SHA") ?: "unknown"
            "${sha.take(7)}-SNAPSHOT"
          }
    }
    // Local development - ALWAYS use localVersion from gradle.properties
    else -> project.findProperty("localVersion") as String? ?: "0.1.0-SNAPSHOT"
  }

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

tasks.withType<Test>().configureEach { useJUnitPlatform() }

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
    targetExclude("**/build/generated/**")
    licenseHeader(licenseHeaderText)
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    ktfmt().googleStyle().configure { it.setRemoveUnusedImports(true) }
    trimTrailingWhitespace()
    endWithNewline()
  }

  format("markdown") {
    target("**/*.md")
    targetExclude("**/build/**")
    prettier().configFile(rootProject.file(".prettierrc.json"))
  }

  yaml {
    target("**/*.yml", "**/*.yaml")
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
  signAllPublications()

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
