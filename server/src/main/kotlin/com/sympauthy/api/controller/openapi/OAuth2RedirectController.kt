package com.sympauthy.api.controller.openapi

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import java.net.URI

/**
 * Redirects the OAuth2 callback from `/oauth2-redirect.html` to `/swagger-ui/oauth2-redirect.html`
 * so that Swagger UI's default redirect URL works regardless of the host/port.
 *
 * Note: Swagger UI's `oauth2RedirectUrl` configuration only supports absolute URLs, making it
 * impossible to configure a portable redirect URL at build time. Instead, we let Swagger UI use
 * its default redirect URL (`<origin>/oauth2-redirect.html`) and redirect to the actual page.
 */
@Secured(IS_ANONYMOUS)
@Controller("/oauth2-redirect.html")
class OAuth2RedirectController {

    @Get
    fun redirect(request: HttpRequest<*>): HttpResponse<*> {
        val query = request.uri.rawQuery
        val target = if (query != null) {
            "/swagger-ui/oauth2-redirect.html?$query"
        } else {
            "/swagger-ui/oauth2-redirect.html"
        }
        return HttpResponse.redirect<Any>(URI.create(target))
    }
}
