package com.sympauthy.business.model.branding

/**
 * What a page needs of the brand besides its stylesheet and its images.
 *
 * It needs the dark mode because choosing the dark logo by the device's preference alone would put it on a light
 * page under [DarkMode.OFF].
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
