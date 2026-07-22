package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.*
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.server.DisplayMessages
import com.sympauthy.util.orDefault
import io.micronaut.context.MessageSource
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import java.util.*

@Secured(HAS_STATE)
@Controller("/api/v1/flow/configuration")
class ConfigurationController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil,
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    @Operation(
        description = """
Return the configuration of the authentication flow associated to the client. It exposes information such as:
- features that are enabled for the client.
- claims collectable by the authorization server.
- etc.

This configuration only contains information that are associated to this authorization server and the client,
they can be cached across the different end-users trying to authenticate.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getConfiguration(
        httpRequest: HttpRequest<*>,
        authentication: Authentication
    ): ConfigurationResource = webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRun(
        state = authentication.stateOrNull
    ) { authorizeAttempt, _ ->
        val locale = httpRequest.locale.orDefault()

        val client = clientManager.findClientByIdOrNull(authorizeAttempt.clientId)
        val audience = client?.audience

        ConfigurationResource(
            claims = getCollectableClaims(locale),
            features = getFeatures(audience)
        )
    }

    private fun getCollectableClaims(locale: Locale): List<CollectableClaimConfigurationResource> {
        return claimManager.listCollectableClaims().map { claim ->
            CollectableClaimConfigurationResource(
                id = claim.id,
                required = claim.required,
                name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
                group = claim.group?.name?.lowercase(),
                type = claim.dataType.name.lowercase()
            )
        }
    }

    private fun getFeatures(
        audience: com.sympauthy.business.model.audience.Audience?
    ): FeaturesResource {
        return FeaturesResource(
            signUp = passwordFlowManager.signUpEnabled,
            signUpEnabled = audience?.signUpEnabled ?: true,
            invitationEnabled = audience?.invitationEnabled ?: false,
        )
    }
}
