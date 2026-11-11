package io.github.kostack.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsPluginTest {
  @TempDir
  lateinit var dir: File

  @Test
  fun `imports the shared catalog as libs and sets the group`() {
    val service = SampleService(dir)
    service.write(
      "build.gradle.kts",
      SampleService.buildScript(dependencies = listOf("implementation(libs.dotenv)"))
    )

    val result = service.build("dependencies", "--configuration", "runtimeClasspath", "properties")

    assertTrue("io.github.cdimascio:dotenv-java:" in result.output, result.output)
    assertTrue("group: io.github.kostack" in result.output, result.output)
  }

  @Test
  fun `imports the shared catalog as kostackLibs next to the service's own catalog`() {
    val service = SampleService(dir)
    service.write(
      "gradle/libs.versions.toml",
      """
      [libraries]
      commons-lang = "org.apache.commons:commons-lang3:3.17.0"
      """
    )
    service.write(
      "build.gradle.kts",
      SampleService.buildScript(
        dependencies =
          listOf(
            "implementation(libs.commons.lang)",
            "implementation(kostackLibs.dotenv)"
          )
      )
    )

    val result = service.build("dependencies", "--configuration", "runtimeClasspath")

    assertTrue("org.apache.commons:commons-lang3:3.17.0" in result.output, result.output)
    assertTrue("io.github.cdimascio:dotenv-java:" in result.output, result.output)
  }
}
