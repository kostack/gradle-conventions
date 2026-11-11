package io.github.kostack.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Per-project settings of the `io.github.kostack.spring-service` convention plugin.
 *
 * ```
 * kostack {
 *   coverageMinimum = 0.7
 *   coverageExclusions.add("&#42;&#42;/generated/&#42;&#42;")
 * }
 * ```
 */
abstract class KostackExtension {
  /** Minimum line-instruction coverage ratio enforced by `jacocoTestCoverageVerification`. */
  abstract val coverageMinimum: Property<Double>

  /** Ant-style patterns of class files excluded from JaCoCo reports and verification. */
  abstract val coverageExclusions: ListProperty<String>

  init {
    coverageMinimum.convention(0.5)
    coverageExclusions.convention(
      listOf(
        "**/command/**",
        "**/configuration/**",
        "**/fixture/**"
      )
    )
  }
}
