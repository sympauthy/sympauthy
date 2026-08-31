package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
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

    private fun claimTemplate(id: String, allowedValues: List<Any>?) = ClaimTemplate(
        id = id,
        enabled = null,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = allowedValues,
        acl = ClaimTemplateAcl(null, null, null, null, null, null, null)
    )

    private fun claimProperties(id: String, dataType: String, templateId: String? = null) =
        ClaimConfigurationProperties(id).apply {
            type = dataType
            template = templateId
        }

    // --- parseAllowedValues ---

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
    fun `parseAllowedValues - Read the values of a string claim as strings`() {
        val ctx = ConfigParsingContext()

        val values = parser.parseAllowedValues(ctx, listOf("mr", "mrs"), "claims.title.allowed-values", STRING)

        assertEquals(listOf("mr", "mrs"), values)
        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    // --- parse ---

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
        val ctx = ConfigParsingContext()
        val templates = mapOf(
            DEFAULT to claimTemplate(DEFAULT, null),
            "ages" to claimTemplate("ages", listOf(18, 21))
        )
        val properties = claimProperties("age", "number", "ages").apply { allowedValues = listOf(30) }

        val claims = parser.parse(ctx, listOf(properties), templates)

        assertEquals(listOf(30L), claims.first { it.id == "age" }.allowedValues)
    }
}
