package com.sympauthy.mail

import com.sympauthy.business.manager.branding.BrandManager
import com.sympauthy.business.manager.branding.BrandTokenResolver
import com.sympauthy.business.model.branding.BrandColorValue
import com.sympauthy.business.model.branding.BrandImageName
import com.sympauthy.business.model.branding.BrandImageName.LOGO
import com.sympauthy.business.model.branding.BrandImageName.LOGO_DARK
import com.sympauthy.business.model.branding.BrandImageType
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.buildUponRoot
import com.sympauthy.config.model.getOrNull
import com.sympauthy.util.wireName
import com.sympauthy.view.BrandingController.Companion.BRANDING_ENDPOINT
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.roundToInt

/**
 * The brand a mail is drawn in, as the `brand` the mail kit (`views/mails/kit.ftl`) reads.
 *
 * Its colours are the ones [BrandTokenResolver] answers the stylesheet under `/branding` with, but resolved to values:
 * a mail client reads no CSS variable. So a mail follows the operator's colours as the sign-in pages do, and the
 * status colours keep their meaning whatever the brand.
 *
 * Its logo is the PNG `/branding/logo.png` answers, by absolute address: a mail client draws no SVG, and a mail is
 * read away from the server. A deployment whose logo is an SVG, or whose root URL is not configured, has mails
 * showing its name alone.
 */
@Singleton
class MailBrandResolver(
    @Inject private val brandTokenResolver: BrandTokenResolver,
    @Inject private val brandManager: BrandManager,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    private val theme get() = brandTokenResolver.theme

    /**
     * The colours, resolved once: the configuration does not change until the server restarts.
     */
    private val palette: Pair<Map<String, String>, Map<String, String>> by lazy { resolvePalette() }

    /**
     * The name a mail is signed under: the deployment's, or SympAuthy's where it configured none.
     */
    val displayName: String get() = brandManager.brand.displayName ?: DEFAULT_DISPLAY_NAME

    /**
     * The `brand` model of a mail. The logos are read at every mail, since a remote one resolves after startup.
     */
    fun resolve(): Map<String, Any> = buildMap {
        put("displayName", displayName)
        put("isSympAuthy", brandManager.brand.displayName == null)
        put("darkMode", theme.darkMode.wireName)
        put("light", palette.first)
        put("dark", palette.second)
        mailLogo(LOGO)?.let { (url, width) ->
            put("logoUrl", url)
            put("logoWidth", width)
        }
        if (theme.darkMode == DarkMode.AUTO) {
            mailLogo(LOGO_DARK)?.let { (url, width) ->
                put("logoDarkUrl", url)
                put("logoDarkWidth", width)
            }
        }
    }

    /**
     * Every colour token by name, light and dark, the dark theme carrying the tokens [BrandTokenResolver] gives a dark
     * value. An alias takes the colour of the token it names in the same theme, or in the light one where the dark
     * theme does not carry that token, as a variable does in the stylesheet.
     */
    private fun resolvePalette(): Pair<Map<String, String>, Map<String, String>> {
        val colors = brandTokenResolver.colors
        val light = colors.associate { it.name to it.light }
        val dark = colors.mapNotNull { color -> color.dark?.let { color.name to it } }.toMap()
        return light.mapValues { (_, value) -> value.resolveIn(light) } to
            dark.mapValues { (_, value) -> value.resolveIn(dark, light) }
    }

    private fun BrandColorValue.resolveIn(vararg themes: Map<String, BrandColorValue>): String = when (this) {
        is BrandColorValue.Literal -> value
        is BrandColorValue.Alias -> themes.firstNotNullOfOrNull { it[token] }?.resolveIn(*themes)
            ?: error("A colour of the design system names $token, which it does not define.")
    }

    /**
     * The address of the PNG a mail shows for [name], versioned by its content so that a mail client's image proxy
     * does not keep the previous logo, and its width drawn [LOGO_HEIGHT] tall: Outlook draws an image whose width is
     * not given at its own size.
     */
    private fun mailLogo(name: BrandImageName): Pair<String, Int>? {
        val urls = uncheckedUrlsConfig.getOrNull() ?: return null
        val asset = brandManager.getMailImage(name) ?: return null
        val size = BrandImageType.pngSizeOrNull(asset.content) ?: return null
        val path = if (name == LOGO_DARK) "logo-dark.png" else "logo.png"
        val url = urls.buildUponRoot()
            .path(BRANDING_ENDPOINT)
            .path(path)
            .queryParam("v", asset.etag.trim('"').take(VERSION_LENGTH))
            .build()
        val width = (LOGO_HEIGHT.toDouble() * size.width / size.height).roundToInt().coerceAtLeast(1)
        return url.toString() to width
    }

    private companion object {
        const val DEFAULT_DISPLAY_NAME = "SympAuthy"

        const val LOGO_HEIGHT = 40
        const val VERSION_LENGTH = 12
    }
}
