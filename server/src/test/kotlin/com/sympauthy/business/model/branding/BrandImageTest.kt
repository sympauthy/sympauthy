package com.sympauthy.business.model.branding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BrandImageTest {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<svg xmlns="http://www.w3.org/2000/svg"/>""",
            "<svg/>",
            "<svg>",
            "\n  <svg>",
            """<?xml version="1.0"?><svg>""",
            "<!-- an export -->\n<svg>",
            """<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/x.dtd"><svg>""",
            """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY a "b>">]><!-- c --><svg>""",
            """<!DOCTYPE svg [<!ENTITY a "b]>">]><svg>""",
            """<!DOCTYPE svg [<!ENTITY a 'b"c'>]><svg>""",
            """<!DOCTYPE svg PUBLIC "a>b" "c"><svg>""",
            """<!DOCTYPE svg [<!-- it's a comment -->]><svg>""",
            """<svg:svg xmlns:svg="http://www.w3.org/2000/svg"/>""",
            "ï»¿<svg>",
        ]
    )
    fun `ofOrNull - Recognises an SVG past what a document may open with`(content: String) {
        val image = BrandImage.ofOrNull(content.toByteArray(Charsets.ISO_8859_1))

        assertEquals(BrandImageType.SVG, image?.type)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "<html><svg></svg></html>",
            "<svgish>",
            "<svg:rect/>",
            "<svg",
            "</svg>",
            "<!-- never closed <svg>",
            "<?xml never closed <svg>",
            "<!DOCTYPE svg [ never closed <svg>",
            """<!DOCTYPE svg "never closed><svg>""",
            "<!DOCTYPE svg <!-- never closed ><svg>",
            "not an image",
        ]
    )
    fun `ofOrNull - Refuses what is not an SVG`(content: String) {
        assertNull(BrandImage.ofOrNull(content.toByteArray()))
    }

    @Test
    fun `ofOrNull - Refuses an SVG in UTF-16`() {
        assertNull(BrandImage.ofOrNull("<svg/>".toByteArray(Charsets.UTF_16)))
    }

    @Test
    fun `ofOrNull - Recognises a PNG by its signature`() {
        assertEquals(BrandImageType.PNG, BrandImage.ofOrNull(png)?.type)
    }

    @Test
    fun `ofOrNull - Refuses a JPEG`() {
        assertNull(BrandImage.ofOrNull(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
    }

    @Test
    fun `ofOrNull - Takes an image at the limit and refuses one over it`() {
        val atLimit = png + ByteArray(BrandImage.MAX_SIZE - png.size)

        assertEquals(BrandImageType.PNG, BrandImage.ofOrNull(atLimit)?.type)
        assertNull(BrandImage.ofOrNull(atLimit + 0))
    }
}
