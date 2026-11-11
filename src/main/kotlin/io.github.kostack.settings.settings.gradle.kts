import io.github.kostack.gradle.KostackVersions

// Settings plugin of a kostack service: repositories, shared version catalog, project plugin version and group.
// Everything here comes from the same release, so the plugin and catalog versions cannot drift apart.

val conventionPluginId = "io.github.kostack.spring-service"
val catalogCoordinates = "${KostackVersions.GROUP}:gradle-conventions-catalog:${KostackVersions.PLUGIN_VERSION}"

// Opt-in for local development against artifacts published with publishToMavenLocal
val useMavenLocal =
  providers
    .gradleProperty("kostack.useMavenLocal")
    .map { it.toBoolean() }
    .getOrElse(false)

// Lets services write `id("io.github.kostack.spring-service")` without a version
pluginManagement {
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == conventionPluginId && requested.version == null) {
        useVersion(KostackVersions.PLUGIN_VERSION)
      }
    }
  }
}

dependencyResolutionManagement {
  repositories {
    if (useMavenLocal) {
      mavenLocal {
        content { includeGroup(KostackVersions.GROUP) }
      }
    }
    mavenCentral()
  }

  versionCatalogs {
    // gradle/libs.versions.toml already becomes `libs`, so the shared catalog takes another name next to it
    val catalogName = if (settingsDir.resolve("gradle/libs.versions.toml").isFile) "kostackLibs" else "libs"
    create(catalogName) {
      from(catalogCoordinates)
    }
  }
}

gradle.rootProject {
  group = KostackVersions.GROUP
}
