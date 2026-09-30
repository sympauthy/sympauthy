package com.sympauthy.business.model.user.claim

/**
 * Generated OpenID Connect claims whose values are managed by the authorization server.
 *
 * These claims are always enabled and read-only, and a deployment configures nothing about them: every
 * key `claims.<id>` accepts is refused where it is written. What they are is held here, and nowhere else.
 *
 * **What they share is where the value comes from, not who it is about.** All of them are produced by
 * this server at runtime rather than collected from a person, and [computedPerAccount] is where the two
 * kinds part: one is an attribute of the account and the other belongs to a single authorization.
 */
enum class GeneratedOpenIdConnectClaim(
    val id: String,
    val verifiedId: String? = null,
    val dataType: ClaimDataType,
    val group: ClaimGroup? = null,
    /**
     * The consentable scope agreeing to which opens this claim, or null where nothing gates it.
     */
    val scope: String? = null,
    /**
     * Whether this claim has a value for an account, which
     * [GeneratedClaimsManager][com.sympauthy.business.manager.GeneratedClaimsManager] can compute from
     * a user id alone.
     *
     * True for the claims that describe the account. False for one that describes a single
     * authorization instead: there is no value to answer for a person in the abstract, so the surfaces
     * that list what this server knows about somebody leave it out rather than answering null for
     * everybody.
     */
    val computedPerAccount: Boolean = true,
    /**
     * The channels this claim already reaches, which no deployment decides.
     *
     * Neither channel reads a generated claim out of the collected claims — the id token claims the
     * subject itself and the `/userinfo` mapper computes both — so nothing filters on this. It is
     * recorded because what a deployment may be told a channel can supply is read off it.
     */
    val publishedIn: Set<ClaimPublication>
) {
    SUBJECT(
        id = OpenIdConnectClaimId.SUB,
        dataType = ClaimDataType.STRING,
        scope = "profile",
        publishedIn = setOf(ClaimPublication.ID_TOKEN, ClaimPublication.USERINFO)
    ),

    /**
     * Published in `/userinfo` alone: the id token carries the issue time of the token rather than the
     * date this person's claims were last collected, and never claimed `updated_at`.
     */
    UPDATED_AT(
        id = OpenIdConnectClaimId.UPDATED_AT,
        dataType = ClaimDataType.NUMBER,
        scope = "profile",
        publishedIn = setOf(ClaimPublication.USERINFO)
    ),

    /**
     * When the person proved a credential of their account, which the id token claims for itself out of
     * the authorization rather than reading it off the account.
     *
     * It is the one entry that is not [computedPerAccount]: an account has no authentication time, only
     * an authorization does, so the surfaces that answer what this server knows about a person leave it
     * out. It is declared here all the same, because that is what keeps a deployment from declaring a
     * claim of its own under the name and shadowing it in the id token.
     *
     * Nothing gates it on consent. Every id token issued for a person states it whatever they agreed
     * to, which is what lets a resource server rely on it being there.
     *
     * **[publishedIn] names the id token alone, and the value travels further than that.** The access
     * token carries it where RFC 9068 §2.2.1 puts it and the introspection response where RFC 9470 §6.2
     * does, and neither is a [ClaimPublication]: that enum models the two OpenID channels a *collected*
     * value is filtered into, and nothing filters this one. What the set is read for here is
     * `claims_supported`, which the id token alone already answers for.
     */
    AUTHENTICATION_TIME(
        id = OpenIdConnectClaimId.AUTH_TIME,
        dataType = ClaimDataType.NUMBER,
        publishedIn = setOf(ClaimPublication.ID_TOKEN),
        computedPerAccount = false
    );

    /**
     * Who may read this claim, which is the same for every generated claim but the scope above.
     *
     * It carries no unconditional client scope, and a file cannot give it one: every channel that
     * publishes a generated claim computes the value rather than reading it out of the claims
     * collected from a person, so the list [UnconditionalAcl] would hold is consulted by nothing.
     */
    val acl: ClaimAcl
        get() = ClaimAcl(
            consent = ConsentAcl(
                scope = scope,
                readableByPerson = true,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = true,
                writableByClient = false,
                writeMaxAuthenticationAge = null
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )

    companion object {

        /** The ids of every generated claim, which is what a claim written in a file is held against. */
        val ids: Set<String> = entries.mapTo(mutableSetOf(), GeneratedOpenIdConnectClaim::id)

        /**
         * The ids of the generated claims that describe an account, which is the set every per-person
         * surface answers for. One this server computes per authorization is absent, so nothing lists
         * it against somebody or answers null for it.
         */
        val idsComputedPerAccount: Set<String> = entries
            .filter(GeneratedOpenIdConnectClaim::computedPerAccount)
            .mapTo(mutableSetOf(), GeneratedOpenIdConnectClaim::id)
    }
}

/**
 * Identifiers of all OpenID Connect claims supported by this application.
 *
 * Used to determine the [ClaimOrigin] of a claim: if its ID is listed here,
 * the claim originates from the OpenID Connect specification.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">Standard claims</a>
 */
object OpenIdConnectClaimId {
    /**
     * When the person proved a credential of their account, which OpenID Connect Core §2 defines and
     * RFC 9068 §2.2.1 places in a JWT access token as well.
     *
     * **It is deliberately absent from [ALL], and a deployment may not declare a claim under this name**
     * — `ClaimsConfigValidator` refuses one. Every other id in this object names something this server
     * may be told about a person and holds a row for; this one is a property of the authentication
     * behind the token, computed per token and never collected.
     *
     * **That is also why it is not a [GeneratedOpenIdConnectClaim]**, which is how `sub` and
     * `updated_at` are kept out of a deployment's hands. A generated claim is a value computed from a
     * user id, and every per-person claim surface publishes one for each account; there is no
     * authentication time to compute for an account, only for an authorization.
     */
    const val AUTH_TIME = "auth_time"

    const val SUB = "sub"
    const val NAME = "name"
    const val GIVEN_NAME = "given_name"
    const val FAMILY_NAME = "family_name"
    const val MIDDLE_NAME = "middle_name"
    const val NICKNAME = "nickname"
    const val PREFERRED_USERNAME = "preferred_username"
    const val PROFILE = "profile"
    const val PICTURE = "picture"
    const val WEBSITE = "website"
    const val EMAIL = "email"
    const val EMAIL_VERIFIED = "email_verified"
    const val GENDER = "gender"
    const val BIRTH_DATE = "birth_date"
    const val ZONE_INFO = "zoneinfo"
    const val LOCALE = "locale"
    const val PHONE_NUMBER = "phone_number"
    const val PHONE_NUMBER_VERIFIED = "phone_number_verified"
    const val UPDATED_AT = "updated_at"
    const val STREET_ADDRESS = "street_address"
    const val LOCALITY = "locality"
    const val REGION = "region"
    const val POSTAL_CODE = "postal_code"
    const val COUNTRY = "country"

    val ALL: Set<String> = setOf(
        AUTH_TIME, SUB, NAME, GIVEN_NAME, FAMILY_NAME, MIDDLE_NAME, NICKNAME,
        PREFERRED_USERNAME, PROFILE, PICTURE, WEBSITE, EMAIL, EMAIL_VERIFIED,
        GENDER, BIRTH_DATE, ZONE_INFO, LOCALE, PHONE_NUMBER, PHONE_NUMBER_VERIFIED,
        UPDATED_AT, STREET_ADDRESS, LOCALITY, REGION, POSTAL_CODE, COUNTRY
    )
}
