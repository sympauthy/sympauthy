package com.sympauthy.business.provider.model

import io.micronaut.http.MutableHttpRequest

interface ProviderCredentials {

    fun <T : Any> authenticate(httpRequest: MutableHttpRequest<T>)
}
