import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
  `version-catalog`
  `maven-publish`
}

group = "io.github.kostack"
version = providers.gradleProperty("projectVersion").getOrElse("1.0.0")

repositories {
  mavenCentral()
  gradlePluginPortal()
}

// Gradle 9 runs on Java 17+, so consumers are not forced to run Gradle itself on Java 25
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
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
  implementation(libs.dotenv)
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
        put("SPRINGDOC_WEBFLUX_UI", libs.springdoc.webflux.ui.asCoordinates())
        put("KOSTACK_EVENT_DISPATCHER", libs.kostack.event.dispatcher.asCoordinates())
        put("KOSTACK_API_ERRORS", libs.kostack.api.errors.asCoordinates())
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

catalog {
  versionCatalog {
    from(files("gradle/libs.versions.toml"))
  }
}

publishing {
  publications {
    create<MavenPublication>("versionCatalog") {
      artifactId = "gradle-conventions-catalog"
      from(components["versionCatalog"])
    }
  }
}
