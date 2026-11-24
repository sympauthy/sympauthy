package com.sympauthy.client

import com.sympauthy.business.provider.config.model.ProviderUserInfoConfig
import com.sympauthy.business.provider.model.ProviderCredentials
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirst

@Singleton
class UserInfoEndpointClient(
    @Inject private val httpClient: HttpClient
) {

    suspend fun fetchUserInfo(
        userInfoConfig: ProviderUserInfoConfig,
        credentials: ProviderCredentials
    ): Map<*, *> {
        val httpRequest = HttpRequest.GET<String>(userInfoConfig.uri)
            .accept(MediaType.APPLICATION_JSON)
        credentials.authenticate(httpRequest)

        return httpClient.retrieve(httpRequest, Map::class.java)
            .awaitFirst()
    }
}
