package io.github.kostack.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A minimal kostack service in [dir], built with the settings and convention plugins as published to the test
 * repository (see `tasks.test` in build.gradle.kts).
 */
class SampleService(
  val dir: File
) {
  init {
    write("gradle.properties", "kostack.testRepository=${File(property("kostack.testRepository")).toURI()}")
    File(property("kostack.editorconfig")).copyTo(dir.resolve(".editorconfig"))
    write("settings.gradle.kts", settings())
    write("build.gradle.kts", buildScript())
    write(
      "src/main/kotlin/sample/SampleApplication.kt",
      """
      package sample

      import org.springframework.boot.autoconfigure.SpringBootApplication
      import org.springframework.boot.runApplication

      @SpringBootApplication
      class SampleApplication

      fun main(args: Array<String>) {
        runApplication<SampleApplication>(*args)
      }
      """
    )
    write(
      "src/main/kotlin/sample/Greeter.kt",
      """
      package sample

      class Greeter {
        fun greet(name: String) = "Hello, ${'$'}name"
      }
      """
    )
    write(
      "src/test/kotlin/sample/GreeterTest.kt",
      """
      package sample

      import kotlin.test.Test
      import kotlin.test.assertEquals

      class GreeterTest {
        @Test
        fun greets() {
          assertEquals("Hello, you", Greeter().greet("you"))
        }
      }
      """
    )
  }

  fun write(
    path: String,
    content: String
  ): File =
    dir.resolve(path).apply {
      parentFile.mkdirs()
      writeText(content.trimIndent() + "\n")
    }

  fun build(vararg arguments: String): BuildResult = runner(*arguments).build()

  fun buildAndFail(vararg arguments: String): BuildResult = runner(*arguments).buildAndFail()

  private fun runner(vararg arguments: String): GradleRunner =
    GradleRunner
      .create()
      .withProjectDir(dir)
      .withArguments(*arguments, "--stacktrace")

  companion object {
    val VERSION: String = property("kostack.version")

    private fun property(name: String): String =
      requireNotNull(System.getProperty(name)) { "System property $name is set by the test task" }

    // Only the artifacts of this build come from the test repository, everything else from the usual repositories
    fun settings(): String =
      """
      pluginManagement {
        repositories {
          exclusiveContent {
            forRepository { maven(providers.gradleProperty("kostack.testRepository").get()) }
            filter {
              includeGroup("io.github.kostack.settings")
              includeGroup("io.github.kostack.spring-service")
              includeModule("io.github.kostack", "gradle-conventions")
            }
          }
          mavenCentral()
          gradlePluginPortal()
        }
      }

      plugins {
        id("io.github.kostack.settings") version "$VERSION"
      }

      dependencyResolutionManagement {
        repositories {
          exclusiveContent {
            forRepository { maven(providers.gradleProperty("kostack.testRepository").get()) }
            filter { includeModule("io.github.kostack", "gradle-conventions-catalog") }
          }
        }
      }

      rootProject.name = "sample"
      """

    /** Build script of the service; [kostack] and [dependencies] are the lines of those blocks. */
    fun buildScript(
      kostack: List<String> = emptyList(),
      dependencies: List<String> = emptyList()
    ): String =
      buildString {
        appendLine("plugins {")
        appendLine("  id(\"io.github.kostack.spring-service\")")
        appendLine("}")
        appendLine()
        appendLine("kostack {")
        appendLine("  coverageExclusions.add(\"**/SampleApplication*\")")
        kostack.forEach { appendLine("  $it") }
        appendLine("}")
        if (dependencies.isNotEmpty()) {
          appendLine()
          appendLine("dependencies {")
          dependencies.forEach { appendLine("  $it") }
          appendLine("}")
        }
      }
  }
}
