import io.github.cdimascio.dotenv.Dotenv
import io.github.kostack.gradle.KostackExtension
import io.github.kostack.gradle.KostackVersions
import org.gradle.testing.jacoco.tasks.JacocoReportBase
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  kotlin("plugin.serialization")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
  id("com.diffplug.spotless")
  jacoco
}

val kostack =
  extensions.create<KostackExtension>("kostack").apply {
    // Fail loudly if a value is changed after the tasks that use it have read it
    coverageMinimum.finalizeValueOnRead()
    coverageExclusions.finalizeValueOnRead()
  }

val springProfile =
  providers
    .gradleProperty("springProfile")
    .orElse(providers.environmentVariable("SPRING_PROFILES_ACTIVE"))
    .getOrElse("dev")

// Variables from docker/.env.<profile> (or docker/.env) plus SPRING_PROFILES_ACTIVE for the given profile
fun applicationEnvironmentFor(profile: String): Map<String, String> {
  val envFilename = if (file("docker/.env.$profile").isFile) ".env.$profile" else ".env"
  val dotenv =
    Dotenv
      .configure()
      .directory(file("docker").absolutePath)
      .filename(envFilename)
      .ignoreIfMissing()
      .load()
  return dotenv.entries().associate { it.key to it.value } + ("SPRING_PROFILES_ACTIVE" to profile)
}

version = providers.gradleProperty("projectVersion").getOrElse("1.0.0")

kotlin {
  jvmToolchain(KostackVersions.JAVA.toInt())

  compilerOptions {
    freeCompilerArgs.add("-Xjsr305=strict")
  }
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:${KostackVersions.SPRING_CLOUD}")
    mavenBom("org.springframework.shell:spring-shell-dependencies:${KostackVersions.SPRING_SHELL}")
  }
}

// Baseline shared by every service; data access (Mongo, R2DBC, Flyway...) stays in the service
dependencies {
  // Kotlin
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("tools.jackson.module:jackson-module-kotlin")

  // Web
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // OpenAPI documentation and Swagger UI
  implementation(KostackVersions.SPRINGDOC_WEBFLUX_UI)

  // Security
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  // Messaging
  implementation("org.springframework.cloud:spring-cloud-starter-stream-rabbit")

  // Shell
  implementation("org.springframework.shell:spring-shell-starter")

  // kostack
  implementation(KostackVersions.KOSTACK_EVENT_DISPATCHER)
  implementation(KostackVersions.KOSTACK_API_ERRORS)
  implementation(KostackVersions.KOSTACK_DATABASE_TOOLS)
  implementation(KostackVersions.KOSTACK_FIXTURES)
  implementation(KostackVersions.KOSTACK_JWT_SECURITY)

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.mockk:mockk:${KostackVersions.MOCKK}")
  testImplementation(kotlin("test"))
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint()
  }
  kotlinGradle {
    target("*.kts")
    ktlint()
  }
}

// Asks git for the hooks directory so worktrees and core.hooksPath are respected
val gitHooksDir =
  providers
    .exec {
      commandLine("git", "rev-parse", "--path-format=absolute", "--git-path", "hooks")
      workingDir = layout.projectDirectory.asFile
    }.standardOutput.asText
    .map { it.trim() }

tasks.register<Copy>("installGitHook") {
  group = "build setup"
  description = "Installs a git pre-commit hook to enforce formatting rules"

  // The project's scripts/pre-commit.sh wins over the hook bundled with this plugin
  val projectHook = layout.projectDirectory.file("scripts/pre-commit.sh").asFile
  val hookScript =
    if (projectHook.isFile) {
      projectHook
    } else {
      resources.text
        .fromString(
          KostackExtension::class.java
            .getResource("pre-commit.sh")!!
            .readText()
        ).asFile()
    }
  inputs.file(hookScript)

  from(hookScript) { rename { "pre-commit" } }
  into(gitHooksDir)
  filePermissions { unix("rwxr-xr-x") }
}

tasks.named<BootJar>("bootJar") {
  archiveBaseName.set("app")
  archiveVersion.set("")
}

tasks.named<BootRun>("bootRun") {
  dependsOn(tasks.named<BootJar>("bootJar"))
  environment(applicationEnvironmentFor(springProfile))
}

// Runs only the RabbitMQ consumers (cloud-consumer profile, no HTTP server), whatever springProfile is set
tasks.register<BootRun>("bootRunConsumer") {
  group = "application"
  description = "Runs the application with only the cloud-consumer profile"

  val bootRun = tasks.named<BootRun>("bootRun")
  classpath = bootRun.get().classpath
  mainClass.set(bootRun.flatMap { it.mainClass })

  environment(applicationEnvironmentFor("cloud-consumer"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  // Always the test profile: fixtures (FixtureConfig) exist only with shell and test, not with dev
  environment(applicationEnvironmentFor("test"))
  testLogging {
    events("passed", "skipped", "failed")
    showStandardStreams = true
  }
  jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off", "--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JacocoReportBase>().configureEach {
  // Resolved lazily, so values set in the service's `kostack { }` block are taken into account
  val classDirs = files(classDirectories.from.toList())
  val exclusions = kostack.coverageExclusions
  classDirectories.setFrom(
    provider {
      classDirs.files.map { directory -> fileTree(directory) { exclude(exclusions.get()) } }
    }
  )
}

tasks.test {
  // Fail fast on formatting before running the (slow) test suite
  mustRunAfter(tasks.spotlessCheck)
  // Always produce a coverage report, even when tests fail
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  reports.xml.required.set(true)
}

tasks.jacocoTestCoverageVerification {
  // Ensure the report exists when the coverage threshold is not met
  dependsOn(tasks.jacocoTestReport)
  violationRules {
    rule {
      limit { minimum = kostack.coverageMinimum.get().toBigDecimal() }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}
