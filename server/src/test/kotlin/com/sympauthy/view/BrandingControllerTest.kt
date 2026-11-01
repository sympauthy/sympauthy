package com.sympauthy.view

import com.sympauthy.business.manager.branding.BrandManager
import com.sympauthy.business.model.branding.Brand
import com.sympauthy.business.model.branding.BrandAsset
import com.sympauthy.business.model.branding.BrandImageName
import com.sympauthy.business.model.branding.DarkMode
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BrandingControllerTest {

    private val logo = BrandAsset("<svg/>".toByteArray(), "image/svg+xml")
    private val stylesheet = BrandAsset(":root {}".toByteArray(), "text/css")

    private val manager = mockk<BrandManager>()

    private val controller = BrandingController(manager, ObjectMapper.getDefault())

    private fun stubLogo() {
        every { manager.getImage(BrandImageName.LOGO) } returns logo
    }

    private fun stubBrand(darkMode: DarkMode = DarkMode.AUTO) {
        every { manager.brand } returns Brand("Acme", darkMode)
    }

    private fun request(ifNoneMatch: String? = null): MutableHttpRequest<Any> = HttpRequest.GET<Any>("/").apply {
        ifNoneMatch?.let { header("If-None-Match", it) }
    }

    private fun HttpResponse<ByteArray>.assertRevalidated(etag: String) {
        assertEquals(etag, header("ETag"))
        assertEquals("no-cache", header("Cache-Control"))
    }

    @Test
    fun `stylesheet - Answers the stylesheet, to be revalidated`() {
        every { manager.stylesheet } returns stylesheet

        val response = controller.stylesheet(request())

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("text/css", response.contentType.get().toString())
        assertArrayEquals(stylesheet.content, response.body())
        response.assertRevalidated(stylesheet.etag)
    }

    @Test
    fun `logo - Keeps what the image holds from running on this origin`() {
        stubLogo()

        val response = controller.logo(request())

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("image/svg+xml", response.contentType.get().toString())
        assertArrayEquals(logo.content, response.body())
        assertEquals(
            "default-src 'none'; style-src 'unsafe-inline'; sandbox",
            response.header("Content-Security-Policy")
        )
        assertEquals("nosniff", response.header("X-Content-Type-Options"))
        response.assertRevalidated(logo.etag)
    }

    @Test
    fun `icon - Answers the icon`() {
        val icon = BrandAsset("<svg>icon</svg>".toByteArray(), "image/svg+xml")
        every { manager.getImage(BrandImageName.ICON) } returns icon

        assertArrayEquals(icon.content, controller.icon(request()).body())
    }

    @Test
    fun `logoDark - Answers the dark logo`() {
        val logoDark = BrandAsset("<svg>dark</svg>".toByteArray(), "image/svg+xml")
        every { manager.getImage(BrandImageName.LOGO_DARK) } returns logoDark

        assertArrayEquals(logoDark.content, controller.logoDark(request()).body())
    }

    @ParameterizedTest
    @ValueSource(strings = ["TAG", "W/TAG", "\"other\", TAG", "*"])
    fun `logo - Answers 304 to a request naming its tag`(ifNoneMatch: String) {
        stubLogo()

        val response = controller.logo(request(ifNoneMatch.replace("TAG", logo.etag)))

        assertEquals(HttpStatus.NOT_MODIFIED, response.status)
        assertNull(response.body())
        response.assertRevalidated(logo.etag)
    }

    @Test
    fun `logo - Answers the image to a request naming another tag`() {
        stubLogo()

        assertEquals(HttpStatus.OK, controller.logo(request(""""other"""")).status)
    }

    @Test
    fun `brand - Answers the name and the dark mode`() {
        stubBrand()

        val response = controller.brand(request())

        assertEquals("""{"display_name":"Acme","dark_mode":"auto"}""", response.body()!!.decodeToString())
        assertEquals("application/json", response.contentType.get().toString())
        response.assertRevalidated(BrandAsset(response.body()!!, "application/json").etag)
    }

    @Test
    fun `brand - Answers off where the dark mode is off`() {
        stubBrand(DarkMode.OFF)

        val body = controller.brand(request()).body()!!.decodeToString()

        assertEquals("""{"display_name":"Acme","dark_mode":"off"}""", body)
    }

    @Test
    fun `brand - Answers 304 to a request naming its tag`() {
        stubBrand()
        val etag = controller.brand(request()).header("ETag")

        val response = controller.brand(request(etag))

        assertEquals(HttpStatus.NOT_MODIFIED, response.status)
    }
}
