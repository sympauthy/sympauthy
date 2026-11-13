package com.sympauthy.business.manager.branding

import com.sympauthy.business.model.branding.BrandColor
import com.sympauthy.business.model.branding.BrandColorValue
import com.sympauthy.business.model.branding.BrandTypeStyle
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.ThemeConfig
import com.sympauthy.config.model.UIConfig
import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * The tokens of the design system this server ships, read once from its `tokens.json`, with each colour the
 * deployment configured under `ui.theme` written over its default.
 *
 * Every rendering of the brand reads them here, so that the sign-in pages and the mails agree on what a role colours
 * and on which tokens the dark theme carries. The configuration does not change until the server restarts, so
 * neither do they.
 */
@Singleton
class BrandTokenResolver(
    @Inject private val resourceResolver: ResourceResolver,
    @Inject private val objectMapper: ObjectMapper,
    @Inject private val uncheckedUIConfig: UIConfig
) {

    /**
     * The theme the deployment configured. A `ui` section that failed reads as one that configured none, since it
     * already takes readiness down and a page or a mail is better drawn in SympAuthy's colours than in none.
     */
    val theme: ThemeConfig = (uncheckedUIConfig as? EnabledUIConfig)?.theme ?: ThemeConfig.DEFAULT

    private val tokens: JsonNode by lazy {
        val stream = resourceResolver.getResourceAsStream(TOKENS_RESOURCE).getOrNull()
            ?: error("$TOKENS_RESOURCE is not in this build.")
        stream.use { objectMapper.readValue(it.readAllBytes(), Argument.of(JsonNode::class.java)) }
            ?: error("$TOKENS_RESOURCE is empty.")
    }

    /**
     * Every colour token, in the order the design system lists them.
     */
    val colors: List<BrandColor> by lazy {
        tokens.child("color").child("tokens").values().map { token ->
            val name = token.string("name")
            val value = token.child("value")
            val role = roleOf(name)
            val darkValue = if (value.isObject) value.get(DARK) else null
            BrandColor(
                name = name,
                light = colorValue(theme.light[role], if (value.isObject) value.string(LIGHT) else value.stringValue),
                dark = darkValue
                    ?.takeUnless { name.startsWith(NAV_PREFIX) }
                    ?.let { colorValue(theme.dark[role], it.stringValue) }
            )
        }
    }

    /**
     * The spacing, radius and size tokens, by name.
     */
    val scalars: Map<String, String> by lazy {
        SCALAR_GROUPS.flatMap { group ->
            tokens.child(group).child("tokens").values().map { it.string("name") to it.string("value") }
        }.toMap()
    }

    /**
     * The font stack of each family, `sans` or `mono`, by family.
     */
    val fonts: Map<String, String> by lazy {
        tokens.child("type").child("families").entries().associate { (family, stack) -> family to stack.stringValue }
    }

    /**
     * The text styles of the type scale, in the order the design system lists them.
     */
    val typeStyles: List<BrandTypeStyle> by lazy {
        tokens.child("type").child("groups").values().flatMap { group ->
            group.child("styles").values().map { style ->
                BrandTypeStyle(
                    name = style.string("name"),
                    family = style.get("family")?.stringValue ?: group.string("family"),
                    fontSize = style.string("fontSize"),
                    lineHeight = style.string("lineHeight"),
                    fontWeight = style.child("fontWeight").coerceStringValue(),
                    letterSpacing = style.get("letterSpacing")?.stringValue
                )
            }
        }
    }

    /**
     * The roles a deployment colours are the design system's names for its tokens, but for the one the design system
     * calls `gold`.
     */
    private fun roleOf(tokenName: String) = if (tokenName == GOLD_TOKEN) ACCENT_ROLE else tokenName

    private fun colorValue(configured: String?, default: String): BrandColorValue {
        if (configured != null) return BrandColorValue.Literal(configured)
        return ALIAS.matchEntire(default)
            ?.let { BrandColorValue.Alias(it.groupValues[1]) }
            ?: BrandColorValue.Literal(default)
    }

    private fun JsonNode.child(name: String): JsonNode =
        get(name) ?: error("$TOKENS_RESOURCE has no $name where expected.")

    private fun JsonNode.string(name: String): String = child(name).stringValue

    private companion object {
        const val TOKENS_RESOURCE = "classpath:branding/tokens.json"

        const val LIGHT = "light"
        const val DARK = "dark"
        const val GOLD_TOKEN = "gold"
        const val ACCENT_ROLE = "accent"
        const val NAV_PREFIX = "nav-"

        val SCALAR_GROUPS = listOf("spacing", "radius", "size")
        val ALIAS = Regex("""^\{(.+)}$""")
    }
}
