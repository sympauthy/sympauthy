package com.sympauthy.business.model.branding

/**
 * A text style of the design system's type scale, `flow-title` or `panel-label`, set in one of its font [family].
 */
data class BrandTypeStyle(
    val name: String,
    val family: String,
    val fontSize: String,
    val lineHeight: String,
    val fontWeight: String,
    val letterSpacing: String?
)
