package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.business.model.user.claim.ClaimPublication.ID_TOKEN
import com.sympauthy.business.model.user.claim.ClaimPublication.USERINFO
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClaimsConfigParserTest {

    private val configParser = ConfigParser()

    private val parser = ClaimsConfigParser(configParser, ClaimAclParser(configParser))

    private fun claimTemplate(
        id: String,
        allowedValues: List<Any>? = null,
        publishedIn: Set<ClaimPublication>? = null
    ) = ClaimTemplate(
        id = id,
        enabled = null,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = allowedValues,
        publishedIn = publishedIn,
        acl = ClaimTemplateAcl(null, null, null, null, null, null, null)
    )

    private fun claimProperties(
        id: String,
        dataType: String,
        templateId: String? = null,
        publishedIn: List<String>? = null
    ) = ClaimConfigurationProperties(id).apply {
        type = dataType
        template = templateId
        this.publishedIn = publishedIn
    }

    private fun parseOne(ctx: ConfigParsingContext, properties: ClaimConfigurationProperties, template: ClaimTemplate) =
        parser.parse(ctx, listOf(properties), mapOf(template.id to template))
            .first { it.id == properties.id }

    @Test
    fun `parse - Publish a claim in no channel where neither it nor its template names one`() {
        val ctx = ConfigParsingContext()

        val claim = parseOne(ctx, claimProperties("loyalty_tier", "string"), claimTemplate(DEFAULT))

        assertEquals(emptySet<ClaimPublication>(), claim.publishedIn)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Publish a claim in the channels its template names`() {
        val ctx = ConfigParsingContext()
        val template = claimTemplate(DEFAULT, publishedIn = setOf(USERINFO))

        val claim = parseOne(ctx, claimProperties("loyalty_tier", "string"), template)

        assertEquals(setOf(USERINFO), claim.publishedIn)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Publish a claim in the channels it names over the ones its template offers`() {
        val ctx = ConfigParsingContext()
        val template = claimTemplate(DEFAULT, publishedIn = setOf(USERINFO))
        val properties = claimProperties("loyalty_tier", "string", publishedIn = listOf("id-token"))

        val claim = parseOne(ctx, properties, template)

        assertEquals(setOf(ID_TOKEN), claim.publishedIn)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Publish a claim in no channel where it names none over the ones its template offers`() {
        val ctx = ConfigParsingContext()
        val template = claimTemplate(DEFAULT, publishedIn = setOf(USERINFO))
        val properties = claimProperties("loyalty_tier", "string", publishedIn = emptyList())

        val claim = parseOne(ctx, properties, template)

        assertEquals(emptySet<ClaimPublication>(), claim.publishedIn)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Report every entry naming no channel against the index it was written at`() {
        val ctx = ConfigParsingContext()
        val properties = claimProperties("loyalty_tier", "string", publishedIn = listOf("id-token", "access-token"))

        val claim = parseOne(ctx, properties, claimTemplate(DEFAULT))

        assertEquals(setOf(ID_TOKEN), claim.publishedIn)
        assertEquals(listOf("config.invalid_enum_value"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.loyalty_tier.published-in[1]"), ctx.errors.map { it.key })
    }

    @Test
    fun `parse - Publish a generated claim in the channels it already reaches`() {
        val ctx = ConfigParsingContext()

        val claims = parser.parse(ctx, emptyList(), mapOf(DEFAULT to claimTemplate(DEFAULT)))

        assertEquals(setOf(ID_TOKEN, USERINFO), claims.first { it.id == "sub" }.publishedIn)
        assertEquals(setOf(USERINFO), claims.first { it.id == "updated_at" }.publishedIn)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Answer a generated claim off the enum, whatever a file wrote under it`() {
        val ctx = ConfigParsingContext()
        val properties = claimProperties("sub", "number", "openid", publishedIn = listOf("userinfo"))
        val templates = mapOf(DEFAULT to claimTemplate(DEFAULT), "openid" to claimTemplate("openid"))

        val sub = parser.parse(ctx, listOf(properties), templates).first { it.id == "sub" }

        assertEquals(STRING, sub.dataType)
        assertEquals(setOf(ID_TOKEN, USERINFO), sub.publishedIn)
        assertEquals(ParsedClaimAcl.NONE, sub.acl)
        // Refusing what the file wrote is the validator's, so the parser reports nothing at all here.
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parseAllowedValues - Return null when there are none`() {
        val ctx = ConfigParsingContext()

        assertNull(parser.parseAllowedValues(ctx, null, "claims.age.allowed-values", NUMBER))
    }

    @Test
    fun `parseAllowedValues - Read the values of a number claim as longs`() {
        // An unquoted entry is bound as an Integer and a quoted one as a String; both are the same number.
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf(18, "21", -1), "claims.age.allowed-values", NUMBER)

        assertEquals(listOf(18L, 21L, -1L), values)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parseAllowedValues - Report every entry of a number claim that is not a whole number`() {
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf(18, "young", 1.5), "claims.age.allowed-values", NUMBER)

        assertEquals(listOf(18L), values)
        assertEquals(listOf("config.invalid_number", "config.invalid_number"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf("claims.age.allowed-values[1]", "claims.age.allowed-values[2]"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `parseAllowedValues - Read the values of a boolean claim as booleans`() {
        // An unquoted entry is bound as a Boolean and a quoted one as a String; both are the same value.
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf(true, "false"), "claims.optin.allowed-values", BOOLEAN)

        assertEquals(listOf(true, false), values)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parseAllowedValues - Report every entry of a boolean claim that names no truth value`() {
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf(true, "maybe"), "claims.optin.allowed-values", BOOLEAN)

        assertEquals(listOf(true), values)
        assertEquals(listOf("config.invalid_boolean"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.optin.allowed-values[1]"), ctx.errors.map { it.key })
    }

    @Test
    fun `parseAllowedValues - Read the values of a string claim as strings`() {
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf("mr", "mrs"), "claims.title.allowed-values", STRING)

        assertEquals(listOf("mr", "mrs"), values)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Read the values a claim inherits from a template as its own type`() {
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "ages" to claimTemplate("ages", listOf(18, 21))
        )

        val claims = parser.parse(ctx, listOf(claimProperties("age", "number", "ages")), templates)

        assertEquals(listOf(18L, 21L), claims.first { it.id == "age" }.allowedValues)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Report an inherited value against the template it is written in`() {
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "ages" to claimTemplate("ages", listOf("young"))
        )

        parser.parse(ctx, listOf(claimProperties("age", "number", "ages")), templates)

        assertEquals(listOf("config.invalid_number"), ctx.errors.map { it.messageId })
        assertEquals(listOf("templates.claims.ages.allowed-values[0]"), ctx.errors.map { it.key })
    }

    @Test
    fun `parse - Read the values a claim declares over the ones its template offers`() {
        // The template's own values could not convert for this claim, so an error appears here the moment
        // the inherited branch is reached at all.
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "ages" to claimTemplate("ages", listOf("young"))
        )
        val properties = claimProperties("age", "number", "ages").apply { allowedValues = listOf(30) }

        val claims = parser.parse(ctx, listOf(properties), templates)

        assertEquals(listOf(30L), claims.first { it.id == "age" }.allowedValues)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Report an inherited value once however many claims of a type inherit it`() {
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "ages" to claimTemplate("ages", listOf("young"))
        )
        val properties = listOf(
            claimProperties("age", "number", "ages"),
            claimProperties("retirement_age", "number", "ages")
        )

        parser.parse(ctx, properties, templates)

        assertEquals(listOf("config.invalid_number"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `parse - Report an inherited value once per type that cannot read it`() {
        // One template, two types: it is convertible for neither, and each is its own mistake to report.
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "shared" to claimTemplate("shared", listOf(" "))
        )
        val properties = listOf(
            claimProperties("age", "number", "shared"),
            claimProperties("title", "string", "shared")
        )

        parser.parse(ctx, properties, templates)

        assertEquals(
            listOf("config.invalid_number", "config.empty"),
            ctx.errors.map { it.messageId }
        )
    }
}
