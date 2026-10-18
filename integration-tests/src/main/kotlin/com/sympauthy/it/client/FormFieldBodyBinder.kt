package com.sympauthy.it.client

import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder
import io.micronaut.http.client.bind.ClientRequestUriContext
import jakarta.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Binds an argument of a generated-client operation annotated `@Body("<field>")` as that one field of the
 * request body, and an unnamed `@Body` as the whole of it.
 *
 * The generator writes one such argument per field of a form-encoded request body, and the framework's own
 * binder hands every `@Body` value to the request as its entire body: each field then replaces the one
 * before it, and the form arrives as the bare value of its last non-null field. Collecting the named
 * arguments into one map, which the client encodes as the form, is what lets the token, introspection and
 * revocation operations send the request the published document describes.
 */
@Singleton
class FormFieldBodyBinder : AnnotatedClientArgumentRequestBinder<Body> {

    override fun getAnnotationType(): Class<Body> = Body::class.java

    override fun bind(
        context: ArgumentConversionContext<Any>,
        uriContext: ClientRequestUriContext,
        value: Any,
        request: MutableHttpRequest<*>,
    ) {
        val field = context.annotationMetadata.stringValue(Body::class.java).getOrNull()
        if (field.isNullOrEmpty()) {
            request.body(value)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val form = request.body.getOrNull() as? MutableMap<String, Any>
            ?: linkedMapOf<String, Any>().also { request.body(it) }
        form[field] = value
    }
}
