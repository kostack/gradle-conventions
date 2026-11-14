# kostack gradle-conventions

Shared Gradle build conventions for kostack Spring Boot services.

This project publishes three artifacts, all released together under the same version:

| Artifact | Coordinates | Purpose |
|---|---|---|
| Settings plugin | plugin id `io.github.kostack.settings` | Repositories, version catalog, group and the version of the convention plugin |
| Convention plugin | plugin id `io.github.kostack.spring-service` | Applies the full build setup of a kostack service |
| Version catalog | `io.github.kostack:gradle-conventions-catalog` | Shared versions for optional libraries |

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). The plugin, its dependencies and the
published catalog are all built from that file.

## Quick start

`settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("io.github.kostack.settings") version "1.0.0"
}

rootProject.name = "my-service"
```

`build.gradle.kts`:

```kotlin
plugins {
  id("io.github.kostack.spring-service") // version comes from the settings plugin
}

description = "My kostack service"

dependencies {
  // Only what is specific to this service
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
}
```

A service also needs:

- `.editorconfig`: run `./gradlew installEditorConfig` and commit the file, so the IDE formats like ktlint. Without
  it, `spotlessCheck` still works: ktlint then uses the `.editorconfig` bundled with the plugin.
- The Gradle wrapper (Gradle 9.x).

To upgrade a service, change only the version of `io.github.kostack.settings`.

## Settings plugin

`io.github.kostack.settings` does the following in the service's `settings.gradle.kts`:

- **Repositories:** adds `mavenCentral()` to `dependencyResolutionManagement`. See [Repositories](#repositories).
- **Version catalog:** imports `io.github.kostack:gradle-conventions-catalog` at the plugin's own version, as `libs`. If
  the service has its own `gradle/libs.versions.toml`, Gradle already turns that file into `libs`. The shared catalog
  is then imported as `kostackLibs` instead.
- **Convention plugin version:** `id("io.github.kostack.spring-service")` without a version resolves to the settings
  plugin's version. An explicit version in the service still wins.
- **Group:** sets the root project's `group` to `io.github.kostack`.

### Repositories

Dependency repositories are declared in settings only. The convention plugin declares none, because by default Gradle
ignores the settings repositories as soon as a project declares any.

A service can add repositories next to the default one:

```kotlin
// settings.gradle.kts, after the plugins block
dependencyResolutionManagement {
  repositories {
    maven("https://repo.example.com/releases")
  }
}
```

To test an unreleased version published with `publishToMavenLocal`, add `mavenLocal()` to the service's
`pluginManagement` repositories and set `kostack.useMavenLocal=true` (in `gradle.properties` or with
`-Pkostack.useMavenLocal=true`). The settings plugin then also reads `io.github.kostack` artifacts from `~/.m2`, and only
that group.

The `pluginManagement { repositories { } }` block has to stay in the service: Gradle needs it to find the settings
plugin itself.

## What the plugin applies

### Plugins

| Plugin | Version |
|---|---|
| `org.jetbrains.kotlin.jvm` | `kotlin` |
| `org.jetbrains.kotlin.plugin.spring` | `kotlin` |
| `org.jetbrains.kotlin.plugin.serialization` | `kotlin` |
| `org.springframework.boot` | `springBoot` |
| `io.spring.dependency-management` | `springDependencyManagement` |
| `com.diffplug.spotless` | `spotless` |
| `jacoco` | bundled with Gradle |

The versions above refer to entries in `libs.versions.toml`. A service must not declare its own versions of these
plugins. They come with the convention plugin.

### Project setup

- Java toolchain `java` (25) and the Kotlin compiler flag `-Xjsr305=strict`.
- Project `version` from the `projectVersion` Gradle property, defaulting to `1.0.0`.
- The Spring Cloud (`springCloud`) and Spring Shell (`springShell`) BOMs are imported, so their libraries need no
  version.
- Spotless with ktlint for `src/**/*.kt` and the root `*.kts` files.

### Dependencies

| Group | Dependencies |
|---|---|
| Kotlin | `kotlinx-coroutines-core`, `kotlinx-coroutines-reactor`, `kotlinx-serialization-json`, `kotlin-reflect`, `jackson-module-kotlin` |
| Web | `spring-boot-starter-webflux`, `spring-boot-starter-validation`, `spring-boot-starter-actuator` |
| OpenAPI | `springdoc-openapi-starter-webflux-ui` |
| Security | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-oauth2-client` |
| Messaging | `spring-cloud-starter-stream-rabbit` |
| Shell | `spring-shell-starter`, `spring-shell-jline` |
| kostack | `event-dispatcher`, `spring-tools`, `database-tools`, `fixtures`, `jwt-security` |
| Test | `spring-boot-starter-test`, `reactor-test`, `kotlin-test`, `kotlin-test-junit5`, `kotlinx-coroutines-test`, `mockk`, `junit-platform-launcher` (runtime) |

Data access is not included, because it differs between services: Mongo, R2DBC, Flyway, JDBC and database drivers.
Add those in the service.

### Tasks

| Task | Description |
|---|---|
| `bootRun` | Builds `bootJar`, then runs the app with the active profile and `--enable-native-access=ALL-UNNAMED` (for JLine) |
| `bootRunConsumer` | Runs the app with the `cloud-consumer` profile only (RabbitMQ consumers, no HTTP server) |
| `bootJar` | Produces `build/libs/app.jar` (no version in the file name), with `Enable-Native-Access: ALL-UNNAMED` in the manifest |
| `test` | JUnit Platform, always runs with the `test` profile, logs passed/skipped/failed and standard streams |
| `jacocoTestReport` | Runs after every `test`, even when tests fail. Writes both HTML and XML reports |
| `jacocoTestCoverageVerification` | Fails the build when coverage is below `kostack.coverageMinimum`. `check` depends on it |
| `spotlessCheck` / `spotlessApply` | Check or fix formatting. `test` runs after `spotlessCheck` when both are requested |
| `installGitHook` | Installs a pre-commit hook that runs `spotlessCheck` and runs `spotlessApply` when it fails |
| `installEditorConfig` | Writes the `.editorconfig` bundled with the plugin to the root of the service, replacing any existing one |

`installGitHook` uses `scripts/pre-commit.sh` from the service when that file exists. Otherwise it uses the hook bundled
with the plugin. It asks git for the hooks directory, so worktrees and `core.hooksPath` are respected.

The bundled `.editorconfig` is a copy of the one in this repository. When the service has an `.editorconfig` in its
root, ktlint uses that file. Otherwise it uses the bundled one. Run `installEditorConfig` again after upgrading the
plugin to pick up rule changes; the diff shows what changed.

## Spring profiles

The active profile is resolved in this order:

1. the `springProfile` Gradle property: `./gradlew bootRun -PspringProfile=prod`
2. the `SPRING_PROFILES_ACTIVE` environment variable
3. `dev`

Each task sets `SPRING_PROFILES_ACTIVE` to its profile:

| Task | Profile |
|---|---|
| `bootRun` | active profile |
| `bootRunConsumer` | `cloud-consumer` |
| `test` | `test` |

## Configuration

The `kostack` extension holds the settings that may differ per service:

```kotlin
kostack {
  coverageMinimum = 0.7
  coverageExclusions.add("**/generated/**")
}
```

| Property | Type | Default | Description |
|---|---|---|---|
| `coverageMinimum` | `Double` | `0.5` | Minimum coverage ratio (0.0 to 1.0) enforced by `jacocoTestCoverageVerification` |
| `coverageExclusions` | `List<String>` | `**/command/**`, `**/configuration/**`, `**/fixture/**` | Ant-style patterns of classes excluded from coverage |

Use `coverageExclusions.add(...)` to extend the defaults, or `coverageExclusions = listOf(...)` to replace them.

Both values are fixed once a task reads them. Set them in the `kostack { }` block of `build.gradle.kts`, not from
task actions.

## Version catalog

The settings plugin imports the catalog automatically. It contains every entry of `libs.versions.toml`:

```kotlin
dependencies {
  testImplementation(libs.mockk)
}
```

| Alias | Library |
|---|---|
| `libs.springdoc.webflux.ui` | `org.springdoc:springdoc-openapi-starter-webflux-ui` (already added by the plugin) |
| `libs.mockk` | `io.mockk:mockk` (already added by the plugin) |
| `libs.kostack.*` | kostack libraries (already added by the plugin) |
| `libs.spring.cloud.bom`, `libs.spring.shell.bom` | BOMs (already imported by the plugin) |

A service with its own `gradle/libs.versions.toml` gets both catalogs: its own as `libs`, and the shared one as
`kostackLibs`. For example, `kostackLibs.mockk`.

## Development

### Project layout

```
gradle-conventions/
├── .editorconfig                                      formatting rules, also bundled with the plugin
├── build.gradle.kts                                   plugin build and publishing
├── gradle/libs.versions.toml                          single source of versions
└── src/main/
    ├── kotlin/
    │   ├── io.github.kostack.settings.settings.gradle.kts the settings plugin
    │   ├── io.github.kostack.spring-service.gradle.kts    the convention plugin
    │   └── io/github/kostack/gradle/KostackExtension.kt   the `kostack { }` extension
    └── resources/io/github/kostack/gradle/pre-commit.sh   default git hook
```

A precompiled script plugin cannot read the version catalog. The `generateKostackVersions` task therefore writes the
versions and coordinates the plugins need into a generated `KostackVersions` object, in
`build/generated/sources/kostackVersions`. This includes the project's own group and version, which the settings
plugin uses to import the matching catalog and convention plugin.

### Changing a version

1. Update the version in `gradle/libs.versions.toml`.
2. Run `./gradlew build publishToMavenLocal -PprojectVersion=<new version>`.
3. Update the `io.github.kostack.settings` version in the services.

### Adding a dependency to every service

- When a BOM manages the version (Spring Boot, Spring Cloud, Spring Shell), add it to the `dependencies` block of
  `io.github.kostack.spring-service.gradle.kts` without a version.
- Otherwise:
  1. Add it to `[libraries]` in `libs.versions.toml`.
  2. Expose it in `generateKostackVersions` in `build.gradle.kts`, for example
     `put("MY_LIB", libs.my.lib.asCoordinates())`.
  3. Use it in the plugin as `implementation(KostackVersions.MY_LIB)`.

### Tests

The tests in `src/test/kotlin` are functional tests with Gradle TestKit. Each one creates a small Spring Boot service
in a temporary directory and runs a real build of it. Before running, the build publishes all artifacts to
`build/test-repository`. The sample services resolve the plugins and catalog from there, so the tests cover the
published artifacts, not just the compiled classes.

| Test | Covers |
|---|---|
| `SpringServicePluginTest` | Full `build` with the configuration cache, `app.jar`, coverage minimum and exclusions, `test` profile, `installGitHook`, bundled `.editorconfig` fallback, `installEditorConfig` |
| `SettingsPluginTest` | Catalog imported as `libs`, or as `kostackLibs` next to a service's own catalog, and the project group |

```shell
./gradlew test                                     # about a minute with a warm cache
./gradlew test --tests '*SettingsPluginTest*'
```

The sample services download their dependencies from Maven Central, so the tests need network access. The first run
takes longer. Running the tests requires `git` on the `PATH`.

The tests run in CI before every release, so a version bump in `libs.versions.toml` that breaks a service blocks the
release.

### Build and publish locally

```shell
./gradlew build                                   # compile the plugins
./gradlew publishToMavenLocal                     # publish everything to ~/.m2, unsigned
./gradlew publishToMavenLocal -PprojectVersion=1.1.0
```

The plugins target Java 17 bytecode, so services can run Gradle on any JDK from 17 up. The services themselves compile
with the `java` toolchain version (25).

## Releasing to Maven Central

Publishing uses [`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/), like the
other kostack libraries. One release publishes these artifacts, all under the same version:

| Artifact | Contents |
|---|---|
| `io.github.kostack:gradle-conventions` | Plugin code: jar, sources, javadoc |
| `io.github.kostack.settings:io.github.kostack.settings.gradle.plugin` | Plugin marker of the settings plugin |
| `io.github.kostack.spring-service:io.github.kostack.spring-service.gradle.plugin` | Plugin marker of the convention plugin |
| `io.github.kostack:gradle-conventions-catalog` | Version catalog (`.toml`), with empty sources and javadoc jars |

Every artifact is signed. Signing runs only when `signingInMemoryKey` is set, which is why `publishToMavenLocal`
works without a key.

### Release steps

```shell
git tag v1.1.0
git push origin v1.1.0
```

The [`publish`](.github/workflows/publish.yml) workflow builds the project, then runs
`publishAndReleaseToMavenCentral` with the version taken from the tag (`v1.1.0` → `1.1.0`). The artifacts appear on
Maven Central a few minutes after the Central Portal finishes validating them.

### Repository secrets

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_IN_MEMORY_KEY` | ASCII-armored private GPG key, base64-encoded |
| `SIGNING_IN_MEMORY_KEY_ID` | Last 8 characters of the key id |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Passphrase of the key |

These are the same secrets the other kostack repositories use.

### Releasing from a local machine

Put the same values in `~/.gradle/gradle.properties` (`mavenCentralUsername`, `mavenCentralPassword`,
`signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`), then run:

```shell
./gradlew publishAndReleaseToMavenCentral -PprojectVersion=1.1.0
```

Use `publishToMavenCentral` instead to upload without releasing, and release from the Central Portal UI after checking
the deployment.
