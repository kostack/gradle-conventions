package io.github.kostack.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SpringServicePluginTest {
  @TempDir
  lateinit var dir: File

  @Test
  fun `builds a service with the configuration cache`() {
    val service = SampleService(dir)

    // Configuration cache problems fail the build, so a stored entry means the plugins are compatible
    val result = service.build("build", "--configuration-cache")

    assertTrue("Configuration cache entry stored." in result.output, result.output)
    assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    assertEquals(TaskOutcome.SUCCESS, result.task(":jacocoTestCoverageVerification")?.outcome, result.output)
    assertTrue(dir.resolve("build/libs/app.jar").isFile, "bootJar produces app.jar")
    assertTrue(dir.resolve("build/reports/jacoco/test/jacocoTestReport.xml").isFile, "XML coverage report")
  }

  @Test
  fun `enforces the coverage minimum and exclusions`() {
    val service = SampleService(dir)
    service.write(
      "src/main/kotlin/sample/Untested.kt",
      """
      package sample

      class Untested {
        fun value(input: Int) = if (input > 0) input * 2 else input - 1
      }
      """
    )
    // Untested too, but under a default exclusion that add() below must keep
    service.write(
      "src/main/kotlin/sample/configuration/UntestedConfig.kt",
      """
      package sample.configuration

      class UntestedConfig {
        fun value(input: Int) = if (input > 0) input * 2 else input - 1
      }
      """
    )
    service.write("build.gradle.kts", SampleService.buildScript(kostack = listOf("coverageMinimum = 0.9")))

    val failure = service.buildAndFail("check")
    assertTrue("Rule violated" in failure.output, failure.output)

    service.write(
      "build.gradle.kts",
      SampleService.buildScript(
        kostack = listOf("coverageMinimum = 0.9", "coverageExclusions.add(\"**/Untested*\")")
      )
    )

    val success = service.build("check")
    assertEquals(TaskOutcome.SUCCESS, success.task(":jacocoTestCoverageVerification")?.outcome, success.output)
    val report = dir.resolve("build/reports/jacoco/test/jacocoTestReport.xml").readText()
    assertTrue("sample/Greeter" in report, "Greeter is in the report")
    assertTrue("sample/Untested" !in report, "Untested is excluded from the report")
    assertTrue("sample/configuration" !in report, "the default exclusions are kept")
    assertTrue("sample/SampleApplication" !in report, "the application entry point is excluded by default")
  }

  @Test
  fun `runs tests with the test profile`() {
    val service = SampleService(dir)
    service.write(
      "src/test/kotlin/sample/EnvironmentTest.kt",
      """
      package sample

      import kotlin.test.Test
      import kotlin.test.assertEquals

      class EnvironmentTest {
        @Test
        fun environment() {
          assertEquals("test", System.getenv("SPRING_PROFILES_ACTIVE"))
        }
      }
      """
    )

    val result = service.build("test")

    assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
  }

  @Test
  fun `installs the bundled git hook`() {
    val service = SampleService(dir)
    ProcessBuilder("git", "init", "--quiet")
      .directory(dir)
      .inheritIO()
      .start()
      .waitFor()
      .also { assertEquals(0, it, "git init") }

    service.build("installGitHook")

    val hook = dir.resolve(".git/hooks/pre-commit")
    assertTrue(hook.canExecute(), "pre-commit hook is executable")
    assertTrue("spotlessCheck" in hook.readText(), "pre-commit hook runs spotlessCheck")
  }

  @Test
  fun `checks formatting with the bundled editorconfig when the service has none`() {
    val service = SampleService(dir)
    dir.resolve(".editorconfig").delete()

    // The sample sources use 2-space indentation, which ktlint's own defaults reject
    val result = service.build("spotlessCheck")

    assertEquals(TaskOutcome.SUCCESS, result.task(":spotlessCheck")?.outcome, result.output)
  }

  @Test
  fun `installs the bundled editorconfig over an existing one`() {
    val service = SampleService(dir)
    val editorConfig = dir.resolve(".editorconfig")
    val expected = editorConfig.readText()
    editorConfig.writeText("[*]\nindent_size = 4\n")

    service.build("installEditorConfig")

    assertEquals(expected, editorConfig.readText(), ".editorconfig is replaced by the bundled one")
  }
}
