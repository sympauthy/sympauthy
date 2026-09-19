package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminConsentResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminConsentListResource
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.collection.ConsentCollectionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.security.SecurityRule.ADMIN_CONSENT_READ
import com.sympauthy.security.SecurityRule.ADMIN_CONSENT_WRITE
import com.sympauthy.security.userId
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.Explode
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/consents")
class AdminConsentController(
    @Inject private val userManager: UserManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val consentCollectionManager: ConsentCollectionManager,
    @Inject private val consentMapper: AdminConsentResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of active consents for a given user. Consents are ordered " +
                "by the date they were granted, oldest first, then by identifier, unless another order is " +
                "asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/users/{userId}/consents/capabilities.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = FILTER_PARAMETER_NAME,
                `in` = ParameterIn.QUERY,
                description = FILTER_PARAMETER_DESCRIPTION,
                style = ParameterStyle.FORM,
                explode = Explode.TRUE,
                schema = Schema(
                    type = "object",
                    additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
                    additionalPropertiesSchema = String::class
                )
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of consents."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:consent:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(ADMIN_CONSENT_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_READ])
    suspend fun listConsents(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(description = SEARCH_PARAMETER_DESCRIPTION) q: String?
    ): AdminConsentListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, consentCollectionManager.capabilities(), sort, q)
        userManager.findByIdOrNull(userId).orNotFound()
        val consents = consentCollectionManager.listUserConsents(userId, criteria, pageParams)
        return AdminConsentListResource(
            consents = consents.items.map(consentMapper::toResource),
            page = consents.page,
            size = consents.size,
            total = consents.total
        )
    }

    @Operation(
        description = "Retrieve what the consent collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "It describes the collection rather than one account, so it reads the same for every user " +
                "identifier and looks none of them up. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:consent:read."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_CONSENT_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_READ])
    suspend fun getConsentCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(consentCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Revoke the active consent for a given user and audience. " +
                "This also revokes all refresh tokens issued for this user+audience pair.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "204", description = "Consent revoked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:consent:write."
            ),
            ApiResponse(responseCode = "404", description = "No active consent found for this user and audience.")
        ]
    )
    @Delete("/{audienceId}")
    @Secured(ADMIN_CONSENT_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONSENT_WRITE])
    @Status(HttpStatus.NO_CONTENT)
    suspend fun revokeConsent(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the audience.") audienceId: String,
        authentication: Authentication
    ) {
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId).orNotFound()
        consentManager.revokeConsent(
            consent = consent,
            revokedBy = ConsentRevokedBy.ADMIN,
            revokedById = authentication.userId
        )
    }
}
