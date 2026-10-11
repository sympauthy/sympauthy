package com.sympauthy.api.controller.openid.discovery

import com.sympauthy.api.controller.openid.discovery.PublicKeySetController.Companion.OPENID_JWKS_ENDPOINT
import com.sympauthy.business.manager.jwt.JwtManager
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(IS_ANONYMOUS)
@Controller(OPENID_JWKS_ENDPOINT)
class PublicKeySetController(
    @Inject private val jwtManager: JwtManager
) {

    @Operation(
        description = "Retrieve the public keys verifying the signatures of the id tokens and access tokens this " +
            "server issues, as a JSON Web Key Set, RFC 7517 §5.",
        tags = ["openiddiscovery"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Key set holding two public keys: the one verifying id tokens and the one " +
                    "verifying access tokens.",
                useReturnTypeSchema = true
            )
        ]
    )
    @Get(produces = ["application/jwk-set+json", APPLICATION_JSON])
    suspend fun getPublicKey(): Map<String, Any> {
        return jwtManager.getPublicKeySet().toJSONObject()
    }

    companion object {
        const val OPENID_JWKS_ENDPOINT = "/.well-known/public.jwk"
    }
}
