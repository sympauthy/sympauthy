package com.sympauthy.api

import com.sympauthy.api.BrandingController.Companion.BRANDING_ENDPOINT
import com.sympauthy.api.resource.BrandResource
import com.sympauthy.business.branding.manager.BrandManager
import com.sympauthy.business.branding.model.BrandAsset
import com.sympauthy.business.branding.model.BrandImageName
import com.sympauthy.platform.util.wireName
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.ObjectMapper
import io.swagger.v3.oas.annotations.Hidden
import jakarta.inject.Inject

/**
 * Answers the deployment's brand, which belongs to no app: one audience and no gate, like the apps' own assets,
 * so it is no API surface and carries no version. `docs/design/branding.md` is why.
 *
 * Everything here is held in memory, so nothing waits. A route declares no type it produces because each answer
 * sets the type of what it holds, and a request accepting any image but naming no other type would otherwise
 * match no route and be told to authenticate.
 */
@Hidden
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller(BRANDING_ENDPOINT, produces = [MediaType.ALL])
class BrandingController(
    @Inject private val brandManager: BrandManager,
    @Inject private val objectMapper: ObjectMapper
) {

    private val brandDocument: BrandAsset by lazy {
        val resource = BrandResource(
            displayName = brandManager.brand.displayName,
            darkMode = brandManager.brand.darkMode.wireName
        )
        BrandAsset(objectMapper.writeValueAsBytes(resource), MediaType.APPLICATION_JSON)
    }

    @Get("/tokens.css")
    fun stylesheet(request: HttpRequest<*>): HttpResponse<ByteArray> = answer(request, brandManager.stylesheet)

    @Get("/icon")
    fun icon(request: HttpRequest<*>): HttpResponse<ByteArray> = answerImage(request, BrandImageName.ICON)

    @Get("/logo")
    fun logo(request: HttpRequest<*>): HttpResponse<ByteArray> = answerImage(request, BrandImageName.LOGO)

    @Get("/logo-dark")
    fun logoDark(request: HttpRequest<*>): HttpResponse<ByteArray> = answerImage(request, BrandImageName.LOGO_DARK)

    /**
     * The logo a mail shows, as a PNG. Not found where [BrandManager.getMailImage] answers none: the deployment
     * configured an SVG, or an image still being fetched.
     */
    @Get("/logo.png")
    fun mailLogo(request: HttpRequest<*>): HttpResponse<ByteArray> = answerMailImage(request, BrandImageName.LOGO)

    @Get("/logo-dark.png")
    fun mailLogoDark(request: HttpRequest<*>): HttpResponse<ByteArray> =
        answerMailImage(request, BrandImageName.LOGO_DARK)

    @Get("/brand.json")
    fun brand(request: HttpRequest<*>): HttpResponse<ByteArray> = answer(request, brandDocument)

    /**
     * An image is answered with the headers that keep an SVG's scripts from running on the server's origin — see
     * `docs/design/branding.md`.
     */
    private fun answerImage(request: HttpRequest<*>, name: BrandImageName): HttpResponse<ByteArray> =
        answer(request, brandManager.getImage(name)).withImageHeaders()

    private fun MutableHttpResponse<ByteArray>.withImageHeaders() = this
        .header(HttpHeaders.CONTENT_SECURITY_POLICY, IMAGE_CONTENT_SECURITY_POLICY)
        .header(X_CONTENT_TYPE_OPTIONS, "nosniff")

    private fun answerMailImage(request: HttpRequest<*>, name: BrandImageName): HttpResponse<ByteArray> {
        val asset = brandManager.getMailImage(name) ?: return HttpResponse.notFound()
        return answer(request, asset).withImageHeaders()
    }

    private fun answer(request: HttpRequest<*>, asset: BrandAsset): MutableHttpResponse<ByteArray> {
        val response = if (matches(request, asset)) {
            HttpResponse.notModified<ByteArray>()
        } else {
            HttpResponse.ok(asset.content).contentType(asset.mediaType)
        }
        return response
            .header(HttpHeaders.ETAG, asset.etag)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
    }

    /**
     * `If-None-Match` compares weakly, and may name several tags or any tag at all.
     */
    private fun matches(request: HttpRequest<*>, asset: BrandAsset): Boolean =
        request.headers.getAll(HttpHeaders.IF_NONE_MATCH)
            .flatMap { it.split(",") }
            .map { it.trim().removePrefix("W/") }
            .any { it == "*" || it == asset.etag }

    companion object {
        const val BRANDING_ENDPOINT = "/branding"

        private const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
        private const val IMAGE_CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox"
    }
}
