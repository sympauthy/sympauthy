package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AkamaiEdgeProviderTest {

    private val provider = AkamaiEdgeProvider()

    @Test
    fun `read - Answer the address the true client header carries`() {
        val observed = provider.read(headersOf("True-Client-IP" to "203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer the fields EdgeScape packs into one header`() {
        val observed = provider.read(headersOf("X-Akamai-Edgescape" to EDGESCAPE))

        assertEquals(
            SecurityContextGeo(
                countryCode = "US",
                regionCode = "MA",
                region = null,
                city = "CAMBRIDGE",
                postalCode = "02138-02142+02238-02239",
                timeZone = "EST"
            ),
            observed.geo
        )
    }

    @Test
    fun `read - Ignore the fields EdgeScape carries that no field here holds`() {
        val observed = provider.read(headersOf("X-Akamai-Edgescape" to "georegion=263,asnum=21399,throughput=vhigh"))

        assertNull(observed.geo)
    }

    @Test
    fun `read - Skip a field the header holds without an equals sign`() {
        val observed = provider.read(headersOf("X-Akamai-Edgescape" to "country_code=US,malformed,city=CAMBRIDGE"))

        assertEquals("US", observed.geo?.countryCode)
        assertEquals("CAMBRIDGE", observed.geo?.city)
    }

    @Test
    fun `read - Answer nothing where neither header arrived`() {
        val observed = provider.read(headersOf("X-Real-IP" to "203.0.113.7"))

        assertNull(observed.ipAddress)
        assertNull(observed.geo)
    }

    private companion object {

        const val EDGESCAPE = "georegion=263,country_code=US,region_code=MA,city=CAMBRIDGE,dma=506," +
            "areacode=617,county=MIDDLESEX,fips=25017,lat=42.3933,long=-71.1333,timezone=EST," +
            "zip=02138-02142+02238-02239,continent=NA,throughput=vhigh,asnum=21399"
    }
}
