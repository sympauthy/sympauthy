package com.sympauthy.business.model.provider

import io.micronaut.http.MutableHttpRequest

interface ProviderCredentials {

    fun <T : Any> authenticate(httpRequest: MutableHttpRequest<T>)
}
