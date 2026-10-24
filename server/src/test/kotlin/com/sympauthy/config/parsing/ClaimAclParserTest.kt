package com.sympauthy.config.parsing

import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.properties.ClaimAclProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClaimAclParserTest {

    private val parser = ClaimAclParser()

    private fun aclProperties(
        collectedInFlow: String? = null,
        readableByPerson: String? = null,
        writableByPerson: String? = null,
        readableByClient: String? = null,
        writableByClient: String? = null
    ): ClaimAclProperties = object : ClaimAclProperties {
        override val collectedInFlow = collectedInFlow
        override val readableByPerson = readableByPerson
        override val writableByPerson = writableByPerson
        override val readableByClient = readableByClient
        override val writableByClient = writableByClient
    }

    private fun template(acl: ClaimTemplateAcl) = ClaimTemplate(
        id = "test",
        enabled = null,
        required = null,
        group = null,
        kind = null,
        audienceId = null,
        allowedValues = null,
        publishedIn = null,
        publishedInWhenRequested = null,
        acl = acl
    )

    @Test
    fun `parseTemplateAcl - Return all nulls when there is no acl`() {
        assertEquals(ParsedClaimAcl.NONE, parser.parseTemplateAcl(null))
    }

    @Test
    fun `parseTemplateAcl - Read every door from the properties`() {
        val result = parser.parseTemplateAcl(
            aclProperties(
                collectedInFlow = "a",
                readableByPerson = "b",
                writableByPerson = "c",
                readableByClient = "d",
                writableByClient = "e"
            )
        )

        assertEquals(ParsedClaimAcl("a", "b", "c", "d", "e"), result)
    }

    @Test
    fun `parseAcl - Fall back to the template door by door`() {
        val result = parser.parseAcl(
            aclProperties(readableByClient = "claim"),
            template(ClaimTemplateAcl(null, null, null, "template", "template write"))
        )

        assertEquals(ParsedClaimAcl(null, null, null, "claim", "template write"), result)
    }

    @Test
    fun `parseAcl - Keep a door the claim shuts over the template's`() {
        val result = parser.parseAcl(
            aclProperties(writableByClient = "false"),
            template(ClaimTemplateAcl(null, null, null, null, """SCOPE_IS_GRANTED("users:claims:write")"""))
        )

        assertEquals("false", result.writableByClient)
    }

    @Test
    fun `parseAcl - Leave every door shut when neither the claim nor a template opens it`() {
        assertEquals(ParsedClaimAcl.NONE, parser.parseAcl(null, null))
    }
}
