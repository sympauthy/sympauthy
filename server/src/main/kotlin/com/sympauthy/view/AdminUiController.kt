package com.sympauthy.view

import com.sympauthy.view.AdminUiController.Companion.ADMIN_UI_ENDPOINT
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.MediaType.TEXT_HTML
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.Hidden
import jakarta.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Serve the index.html of sympauthy-admin that have been added in the resources by the CI.
 */
@Hidden
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller(ADMIN_UI_ENDPOINT)
@Requires(property = "admin.enabled", value = "true")
@Requires(property = "admin.integrated-ui", value = "true")
class AdminUiController(
    @Inject private val resourceResolver: ResourceResolver,
) {

    @Get(value = "/{path:[^\\.]*}", produces = [TEXT_HTML])
    fun forward(path: String?): StreamedFile? {
        return resourceResolver.getResource("classpath:sympauthy-admin/index.html")
            ?.map(::StreamedFile)
            ?.getOrNull()
    }

    companion object {
        const val ADMIN_UI_ENDPOINT = "/admin"
    }
}
