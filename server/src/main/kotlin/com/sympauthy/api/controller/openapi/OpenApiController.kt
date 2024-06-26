package com.sympauthy.api.controller.openapi

import com.sympauthy.OpenAPI
import com.sympauthy.api.controller.openapi.OpenApiController.Companion.OPENAPI_ENDPOINT
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpStatus.NOT_FOUND
import io.micronaut.http.MediaType.APPLICATION_YAML
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.reactivex.rxjava3.core.Single
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * This controller read the OpenAPI generated by the toolchain and replace the {url} by the url of this server.
 */
@Secured(IS_ANONYMOUS)
@Controller(OPENAPI_ENDPOINT)
class OpenApiController(
    @Inject private val urlsConfig: UrlsConfig
) {

    private val openApiFilename = Single.create {
        val annotation = OpenAPI::class.annotations
            .filterIsInstance<OpenAPIDefinition>()
            .firstOrNull()
        if (annotation != null) {
            it.onSuccess("${annotation.info.title.lowercase()}-${annotation.info.version}.yml")
        } else {
            it.onError(httpExceptionOf(NOT_FOUND, "not_found"))
        }
    }.cache()

    @Get(produces = [APPLICATION_YAML])
    suspend fun getOpenApi(): String {
        return replaceUrlInOpenApi()
    }

    private suspend fun replaceUrlInOpenApi(): String {
        val rootUrl = urlsConfig.orThrow().root.toString()
        val resourceName = openApiFilename.await().let { "META-INF/swagger/$it" }
        return withContext(Dispatchers.IO) {
            OpenAPI::class.java.classLoader.getResourceAsStream(resourceName)?.use {
                it.readAllBytes().toString(Charset.forName("UTF-8"))
                    .replace("{rootUrl}", rootUrl)
            }
        } ?: throw httpExceptionOf(NOT_FOUND, "not_found")
    }

    companion object {
        const val OPENAPI_ENDPOINT = "/openapi.yml"
    }
}
