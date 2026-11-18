package com.sympauthy.business.model.branding

/**
 * What a page needs of the brand besides its stylesheet and its images, the dark mode included; see
 * `docs/design/branding.md`.
 */
data class Brand(
    val displayName: String?,
    val darkMode: DarkMode
)

enum class DarkMode {
    /**
     * The pages follow the device.
     */
    AUTO,

    /**
     * The pages are always light.
     */
    OFF
}
