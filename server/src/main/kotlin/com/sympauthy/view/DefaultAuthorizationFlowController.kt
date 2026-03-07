package com.sympauthy.view

import com.sympauthy.view.DefaultAuthorizationFlowController.Companion.USER_FLOW_ENDPOINT
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.MediaType.TEXT_HTML
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile
import io.swagger.v3.oas.annotations.Hidden
import jakarta.inject.Inject
import kotlin.jvm.optionals.getOrNull


/**
 * Serve the index.html of sympauthy-flow that have been added in the resources by the CI.
 */
@Hidden
@Controller(USER_FLOW_ENDPOINT)
class DefaultAuthorizationFlowController(
    @Inject private val resourceResolver: ResourceResolver,
) {

    @Get(value = "/{path:[^\\.]*}", produces = [TEXT_HTML])
    fun forward(path: String?): StreamedFile? {
        return resourceResolver.getResource("classpath:sympauthy-flow/index.html")
            ?.map(::StreamedFile)
            ?.getOrNull()
    }

    companion object {
        const val USER_FLOW_ENDPOINT = "/flow"
    }
}
