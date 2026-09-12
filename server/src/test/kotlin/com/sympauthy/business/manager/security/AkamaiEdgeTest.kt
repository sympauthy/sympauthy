package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AkamaiEdgeTest {

    private val edge = AkamaiEdge()

    @Test
    fun `readGeoOrNull - Answer the fields EdgeScape packs into one header`() {
        assertEquals(
            SecurityContextGeo(
                countryCode = "US",
                regionCode = "MA",
                region = null,
                city = "CAMBRIDGE",
                postalCode = "02138-02142+02238-02239",
                timeZone = "EST"
            ),
            edge.readGeoOrNull(headersOf("X-Akamai-Edgescape" to EDGESCAPE))
        )
    }

    @Test
    fun `readGeoOrNull - Ignore the fields EdgeScape carries that no field here holds`() {
        assertNull(edge.readGeoOrNull(headersOf("X-Akamai-Edgescape" to "georegion=263,asnum=21399,throughput=vhigh")))
    }

    @Test
    fun `readGeoOrNull - Skip a field the header holds without an equals sign`() {
        val geo = edge.readGeoOrNull(headersOf("X-Akamai-Edgescape" to "country_code=US,malformed,city=CAMBRIDGE"))

        assertEquals("US", geo?.countryCode)
        assertEquals("CAMBRIDGE", geo?.city)
    }

    @Test
    fun `readGeoOrNull - Answer nothing where the header did not arrive`() {
        assertNull(edge.readGeoOrNull(headersOf("X-Real-IP" to "203.0.113.7")))
    }

    private companion object {

        const val EDGESCAPE = "georegion=263,country_code=US,region_code=MA,city=CAMBRIDGE,dma=506," +
            "areacode=617,county=MIDDLESEX,fips=25017,lat=42.3933,long=-71.1333,timezone=EST," +
            "zip=02138-02142+02238-02239,continent=NA,throughput=vhigh,asnum=21399"
    }
}
