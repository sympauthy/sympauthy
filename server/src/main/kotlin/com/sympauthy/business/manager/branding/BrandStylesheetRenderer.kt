package com.sympauthy.business.manager.branding

import com.sympauthy.business.model.branding.BrandColorValue
import com.sympauthy.business.model.branding.BrandTypeStyle
import com.sympauthy.business.model.branding.DarkMode
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Writes the stylesheet under `/branding`: every token of the design system this server ships, with each colour
 * the deployment configured written over its default.
 *
 * `tokens.json` is the design system's own file, and this writes what its `build-theme.mjs` writes for
 * `tokens.css` but for the two things the design system does not have to answer: the dark roles apply under the
 * device's preference, and they are left out under [DarkMode.OFF].
 */
@Singleton
class BrandStylesheetRenderer(
    @Inject private val brandTokenResolver: BrandTokenResolver
) {

    fun render(): String = buildString {
        append(HEADER)
        val colors = brandTokenResolver.colors
        appendBlock("", LIGHT_SELECTOR, colors.map { colorDeclaration(it.name, it.light) })
        if (brandTokenResolver.theme.darkMode == DarkMode.AUTO) {
            val dark = colors.mapNotNull { color -> color.dark?.let { colorDeclaration(color.name, it) } }
            append("@media (prefers-color-scheme: dark) {\n")
            appendBlock("  ", SYSTEM_DARK_SELECTOR, dark)
            append("}\n")
            appendBlock("", PINNED_DARK_SELECTOR, dark)
        }
        appendBlock("", ":root", scalars() + fonts())
        brandTokenResolver.typeStyles.forEach { append(typeStyle(it)).append('\n') }
    }

    /**
     * An alias is written as the variable it names, so that it follows the colour configured for it.
     */
    private fun colorDeclaration(name: String, value: BrandColorValue): String = when (value) {
        is BrandColorValue.Literal -> declaration(name, value.value)
        is BrandColorValue.Alias -> declaration(name, "var(--${value.token})")
    }

    private fun scalars(): List<String> = brandTokenResolver.scalars.map { (name, value) -> declaration(name, value) }

    /**
     * The variable fonts the apps serve come first, and the static family names stay as fallbacks.
     */
    private fun fonts(): List<String> = brandTokenResolver.fonts.map { (family, stack) ->
        val variable = VARIABLE_FONTS[family]
        "--font-$family: ${listOfNotNull(variable, stack).joinToString(", ")};"
    }

    private fun typeStyle(style: BrandTypeStyle): String {
        val declarations = listOfNotNull(
            "font-family: var(--font-${style.family});",
            "font-size: ${style.fontSize};",
            "line-height: ${style.lineHeight};",
            "font-weight: ${style.fontWeight};",
            style.letterSpacing?.let { "letter-spacing: $it;" }
        )
        return ".${style.name} { ${declarations.joinToString(" ")} }"
    }

    private fun declaration(name: String, value: String) = "--${name.replace(".", "\\.")}: $value;"

    private fun StringBuilder.appendBlock(indent: String, selector: String, declarations: List<String>) {
        append(indent).append(selector).append(" {\n")
        declarations.forEach { append(indent).append("  ").append(it).append('\n') }
        append(indent).append("}\n")
    }

    private companion object {
        const val HEADER = "/* @sympauthy/design tokens, with this deployment's colours written over them. */\n"

        const val LIGHT_SELECTOR = ":root, [data-theme=\"light\"]"
        const val SYSTEM_DARK_SELECTOR = ":root:not([data-theme=\"light\"])"
        const val PINNED_DARK_SELECTOR = "[data-theme=\"dark\"]"

        val VARIABLE_FONTS = mapOf(
            "sans" to "'Plus Jakarta Sans Variable'",
            "mono" to "'JetBrains Mono Variable'"
        )
    }
}
