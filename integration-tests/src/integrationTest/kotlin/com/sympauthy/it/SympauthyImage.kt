package com.sympauthy.it

import org.testcontainers.utility.DockerImageName

/**
 * Resolves the SympAuthy Docker image the integration tests run against.
 *
 * Resolution order (first non-blank wins):
 *  1. the `sympauthy.image` system property (e.g. `-Dsympauthy.image=sympauthy:it`),
 *  2. the `SYMPAUTHY_IMAGE` environment variable,
 *  3. the published nightly image [DEFAULT_IMAGE].
 *
 * The default does not hold the working tree; which image to point the suite at instead is
 * `docs/design/running-locally.md`.
 */
object SympauthyImage {

    /** Published image the library declares its container compatible with; also the default we run. */
    const val DEFAULT_IMAGE = "ghcr.io/sympauthy/sympauthy-nightly:latest"

    /** Repository any override image must be declared a compatible substitute for. */
    private const val COMPATIBLE_REPOSITORY = "ghcr.io/sympauthy/sympauthy-nightly"

    fun resolve(): DockerImageName {
        val reference = System.getProperty("sympauthy.image").orNull()
            ?: System.getenv("SYMPAUTHY_IMAGE").orNull()
            ?: DEFAULT_IMAGE
        // A locally built image (e.g. `sympauthy:it`) has a different repository, so it must be
        // explicitly declared a compatible substitute or SympauthyContainer's constructor rejects it.
        return DockerImageName.parse(reference).asCompatibleSubstituteFor(COMPATIBLE_REPOSITORY)
    }

    private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
