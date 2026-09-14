package com.sympauthy.view

import com.sympauthy.view.SwaggerUiController.Companion.SWAGGER_UI_ENDPOINT
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Hidden
import java.net.URI

/**
 * Redirect `/swagger-ui` and `/swagger-ui/`, which the framework routes here alike, to the index of the
 * Swagger UI the toolchain generates, served from the static resources under the same prefix.
 *
 * Swagger UI derives the redirect URI it sends from the path of the page it runs on, and its own
 * `oauth2RedirectUrl` setting takes an absolute URL, which no build of this server can know. Landing every
 * visitor inside the directory is what leaves a single URI for a client to allow,
 * `<root>/swagger-ui/oauth2-redirect.html`, the one the `admin` client of `application-admin.yml` lists.
 * The target is the index rather than the directory because a redirect to the directory would match this
 * route again.
 */
@Hidden
@Secured(IS_ANONYMOUS)
@Controller(SWAGGER_UI_ENDPOINT)
class SwaggerUiController {

    @Get
    fun redirect(): HttpResponse<*> = HttpResponse.seeOther<Any>(URI.create("$SWAGGER_UI_ENDPOINT/index.html"))

    companion object {
        const val SWAGGER_UI_ENDPOINT = "/swagger-ui"
    }
}
