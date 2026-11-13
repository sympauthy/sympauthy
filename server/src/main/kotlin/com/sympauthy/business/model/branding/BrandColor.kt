package com.sympauthy.business.model.branding

/**
 * A colour token of the design system, with the colour the deployment configured for its role written over its
 * default.
 *
 * [dark] is null for a token the dark theme does not carry: one with a single value, or a `nav-*` one, which the
 * sidebar shows whatever the theme, or a dark page would read SympAuthy's sidebar over the deployment's.
 */
data class BrandColor(
    val name: String,
    val light: BrandColorValue,
    val dark: BrandColorValue?
)

/**
 * The value of a colour token in one theme.
 */
sealed class BrandColorValue {

    /**
     * A colour, as CSS writes it.
     */
    data class Literal(val value: String) : BrandColorValue()

    /**
     * The colour of another token, written `{primary}` in `tokens.json`, so that it follows the one configured for
     * that token.
     */
    data class Alias(val token: String) : BrandColorValue()
}
