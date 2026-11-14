import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
  `version-catalog`
  // Build tooling of this repository only, so not part of the published catalog
  id("com.vanniktech.maven.publish.base") version "0.33.0"
}

group = "io.github.kostack"
version = providers.gradleProperty("projectVersion").getOrElse("1.1.1-rc1")
description = "Gradle conventions for kostack Spring Boot services"

repositories {
  mavenCentral()
  gradlePluginPortal()
}

// Gradle 9 runs on Java 17+, so consumers are not forced to run Gradle itself on Java 25
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17

  // Required by Maven Central
  withSourcesJar()
  withJavadocJar()
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

// Plugin marker coordinates, so the [plugins] section of the catalog is the single source of plugin versions
fun Provider<PluginDependency>.asDependency(): Provider<String> =
  map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
  implementation(libs.plugins.kotlin.jvm.asDependency())
  implementation(libs.plugins.kotlin.spring.asDependency())
  implementation(libs.plugins.kotlin.serialization.asDependency())
  implementation(libs.plugins.spring.boot.asDependency())
  implementation(libs.plugins.spring.dependency.management.asDependency())
  implementation(libs.plugins.spotless.asDependency())

  // Tests of this repository only, so not part of the published catalog
  testImplementation(platform("org.junit:junit-bom:6.0.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(gradleTestKit())
}

// "group:name:version" of a catalog library
fun Provider<MinimalExternalModuleDependency>.asCoordinates(): Provider<String> =
  map { "${it.module}:${it.versionConstraint.requiredVersion}" }

// Exposes catalog versions to the convention plugin at runtime (precompiled scripts cannot read the catalog)
val generateKostackVersions =
  tasks.register("generateKostackVersions") {
    val outputDir = layout.buildDirectory.dir("generated/sources/kostackVersions/kotlin")
    val versions =
      objects.mapProperty<String, String>().apply {
        put("GROUP", project.group.toString())
        put("PLUGIN_VERSION", project.version.toString())
        put("JAVA", libs.versions.java)
        put("SPRING_CLOUD", libs.versions.springCloud)
        put("SPRING_SHELL", libs.versions.springShell)
        put("MOCKK", libs.versions.mockk)
        put("SPRINGDOC_WEBFLUX_SCALAR", libs.springdoc.webflux.scalar.asCoordinates())
        put("KOSTACK_EVENT_DISPATCHER", libs.kostack.event.dispatcher.asCoordinates())
        put("KOSTACK_SPRING_TOOLS", libs.kostack.spring.tools.asCoordinates())
        put("KOSTACK_DATABASE_TOOLS", libs.kostack.database.tools.asCoordinates())
        put("KOSTACK_FIXTURES", libs.kostack.fixtures.asCoordinates())
        put("KOSTACK_JWT_SECURITY", libs.kostack.jwt.security.asCoordinates())
      }
    inputs.property("versions", versions)
    outputs.dir(outputDir)

    doLast {
      val constants = versions.get().entries.joinToString("\n") { (name, value) -> "  const val $name = \"$value\"" }
      outputDir.get().file("io/github/kostack/gradle/KostackVersions.kt").asFile.apply {
        parentFile.mkdirs()
        writeText("package io.github.kostack.gradle\n\ninternal object KostackVersions {\n$constants\n}\n")
      }
    }
  }

sourceSets.main {
  kotlin.srcDir(generateKostackVersions)
}

// The repository's own .editorconfig is the one bundled with the plugin, so there is a single copy to maintain
tasks.processResources {
  from(".editorconfig") {
    into("io/github/kostack/gradle")
    rename { "editorconfig" }
  }
}

catalog {
  versionCatalog {
    from(files("gradle/libs.versions.toml"))
  }
}

gradlePlugin {
  website = "https://github.com/kostack/gradle-conventions"
  vcsUrl = "https://github.com/kostack/gradle-conventions"

  plugins {
    named("io.github.kostack.settings") {
      displayName = "kostack settings"
      description = "Repositories, shared version catalog and convention plugin version for kostack services"
    }
    named("io.github.kostack.spring-service") {
      displayName = "kostack Spring service"
      description = "Build conventions for kostack Spring Boot services"
    }
  }
}

// Maven Central requires javadoc and sources jars next to every artifact, so the catalog gets empty ones
val catalogJavadocJar =
  tasks.register<Jar>("catalogJavadocJar") {
    archiveBaseName = "gradle-conventions-catalog"
    archiveClassifier = "javadoc"
  }
val catalogSourcesJar =
  tasks.register<Jar>("catalogSourcesJar") {
    archiveBaseName = "gradle-conventions-catalog"
    archiveClassifier = "sources"
    from("gradle/libs.versions.toml")
  }

// Local repository the functional tests resolve the plugins and catalog from, exactly as published
val testRepository = layout.buildDirectory.dir("test-repository")

publishing {
  repositories {
    maven {
      name = "test"
      url = uri(testRepository)
    }
  }
  publications {
    create<MavenPublication>("versionCatalog") {
      artifactId = "gradle-conventions-catalog"
      from(components["versionCatalog"])
      artifact(catalogJavadocJar)
      artifact(catalogSourcesJar)
    }
  }
}

// Applies to every publication: both plugins, their markers and the catalog
mavenPublishing {
  publishToMavenCentral()

  // Maven Central requires signatures; the key is set in CI, publishToMavenLocal works without it
  if (providers.gradleProperty("signingInMemoryKey").isPresent) {
    signAllPublications()
  }

  pom {
    name = "kostack gradle-conventions"
    description = project.description
    url = "https://github.com/kostack/gradle-conventions"

    licenses {
      license {
        name = "MIT License"
        url = "https://opensource.org/licenses/MIT"
      }
    }

    developers {
      developer {
        id = "zender"
        name = "Nikolay Georgiev"
        url = "https://github.com/zender"
      }
    }

    scm {
      connection = "scm:git:git://github.com/kostack/gradle-conventions.git"
      developerConnection = "scm:git:ssh://github.com/kostack/gradle-conventions.git"
      url = "https://github.com/kostack/gradle-conventions"
    }
  }
}

tasks.test {
  useJUnitPlatform()
  dependsOn("publishAllPublicationsToTestRepository")

  systemProperty("kostack.testRepository", testRepository.get().asFile.absolutePath)
  systemProperty("kostack.version", version.toString())
  systemProperty("kostack.editorconfig", file(".editorconfig").absolutePath)
  inputs.file(".editorconfig")

  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
