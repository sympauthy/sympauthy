package com.sympauthy.api.util

import com.sympauthy.api.exception.httpExceptionOf
import io.micronaut.http.HttpStatus.NOT_FOUND

fun <T : Any> T?.orNotFound(): T {
    return this ?: throw httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
}
