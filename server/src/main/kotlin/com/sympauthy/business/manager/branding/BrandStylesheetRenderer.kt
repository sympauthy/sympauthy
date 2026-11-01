package com.sympauthy.business.manager.branding

import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.model.ThemeConfig
import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.jvm.optionals.getOrNull

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
    @Inject private val resourceResolver: ResourceResolver,
    @Inject private val objectMapper: ObjectMapper
) {

    private val tokens: JsonNode by lazy {
        val stream = resourceResolver.getResourceAsStream(TOKENS_RESOURCE).getOrNull()
            ?: error("$TOKENS_RESOURCE is not in this build.")
        stream.use { objectMapper.readValue(it.readAllBytes(), Argument.of(JsonNode::class.java)) }
            ?: error("$TOKENS_RESOURCE is empty.")
    }

    fun render(theme: ThemeConfig): String = buildString {
        append(HEADER)
        val colors = tokens.child("color").child("tokens").values().toList()
        appendBlock("", LIGHT_SELECTOR, colors.map { colorDeclaration(it, LIGHT, theme.light) })
        if (theme.darkMode == DarkMode.AUTO) {
            val dark = colors.filter(::hasDarkValue).map { colorDeclaration(it, DARK, theme.dark) }
            append("@media (prefers-color-scheme: dark) {\n")
            appendBlock("  ", SYSTEM_DARK_SELECTOR, dark)
            append("}\n")
            appendBlock("", PINNED_DARK_SELECTOR, dark)
        }
        appendBlock("", ":root", scalars() + fonts())
        typeStyles().forEach { append(it).append('\n') }
    }

    /**
     * What a deployment configured for [token] in [colors], or its value in [theme]. An alias, `{primary}`, is
     * written as the variable it names, so that it follows the colour configured for it.
     */
    private fun colorDeclaration(token: JsonNode, theme: String, colors: Map<String, String>): String {
        val name = token.string("name")
        val value = token.child("value").let { if (it.isObject) it.string(theme) else it.stringValue }
        return declaration(name, colors[roleOf(name)] ?: ALIAS.replace(value) { "var(--${it.groupValues[1]})" })
    }

    /**
     * The roles a deployment colours are the design system's names for its tokens, but for the one the
     * stylesheet calls `--gold`.
     */
    private fun roleOf(tokenName: String) = if (tokenName == GOLD_TOKEN) ACCENT_ROLE else tokenName

    /**
     * Whether [token] is one the dark theme carries. `nav-*` have dark values and are not: they sit on `:root`
     * whatever the theme, or a dark page would read SympAuthy's sidebar over the deployment's.
     */
    private fun hasDarkValue(token: JsonNode): Boolean {
        val value = token.child("value")
        return value.isObject && value.get(DARK) != null && !token.string("name").startsWith(NAV_PREFIX)
    }

    private fun scalars(): List<String> = SCALAR_GROUPS.flatMap { group ->
        tokens.child(group).child("tokens").values().map { declaration(it.string("name"), it.string("value")) }
    }

    /**
     * The variable fonts the apps serve come first, and the static family names stay as fallbacks.
     */
    private fun fonts(): List<String> = tokens.child("type").child("families").entries().map { (family, stack) ->
        val variable = VARIABLE_FONTS[family]
        "--font-$family: ${listOfNotNull(variable, stack.stringValue).joinToString(", ")};"
    }

    private fun typeStyles(): List<String> = tokens.child("type").child("groups").values().flatMap { group ->
        group.child("styles").values().map { style ->
            val family = style.get("family")?.stringValue ?: group.string("family")
            val declarations = listOfNotNull(
                "font-family: var(--font-$family);",
                "font-size: ${style.string("fontSize")};",
                "line-height: ${style.string("lineHeight")};",
                "font-weight: ${style.child("fontWeight").coerceStringValue()};",
                style.get("letterSpacing")?.let { "letter-spacing: ${it.stringValue};" }
            )
            ".${style.string("name")} { ${declarations.joinToString(" ")} }"
        }
    }

    private fun JsonNode.child(name: String): JsonNode =
        get(name) ?: error("$TOKENS_RESOURCE has no $name where expected.")

    private fun JsonNode.string(name: String): String = child(name).stringValue

    private fun declaration(name: String, value: String) = "--${name.replace(".", "\\.")}: $value;"

    private fun StringBuilder.appendBlock(indent: String, selector: String, declarations: List<String>) {
        append(indent).append(selector).append(" {\n")
        declarations.forEach { append(indent).append("  ").append(it).append('\n') }
        append(indent).append("}\n")
    }

    private companion object {
        const val TOKENS_RESOURCE = "classpath:branding/tokens.json"
        const val HEADER = "/* @sympauthy/design tokens, with this deployment's colours written over them. */\n"

        const val LIGHT = "light"
        const val DARK = "dark"
        const val LIGHT_SELECTOR = ":root, [data-theme=\"light\"]"
        const val SYSTEM_DARK_SELECTOR = ":root:not([data-theme=\"light\"])"
        const val PINNED_DARK_SELECTOR = "[data-theme=\"dark\"]"

        const val GOLD_TOKEN = "gold"
        const val ACCENT_ROLE = "accent"
        const val NAV_PREFIX = "nav-"

        val SCALAR_GROUPS = listOf("spacing", "radius", "size")
        val ALIAS = Regex("""^\{(.+)}$""")
        val VARIABLE_FONTS = mapOf(
            "sans" to "'Plus Jakarta Sans Variable'",
            "mono" to "'JetBrains Mono Variable'"
        )
    }
}
