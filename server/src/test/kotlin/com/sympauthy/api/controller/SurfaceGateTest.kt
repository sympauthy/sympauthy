package com.sympauthy.api.controller

import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.compiledClassesOf
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.SecurityRule.IS_CLIENT
import com.sympauthy.security.SecurityRule.IS_USER
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.HttpMethodMapping
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Holds the gate of every route to the surface its prefix names, and every route gated otherwise to a
 * departure listed here with its reason.
 */
class SurfaceGateTest {

    private val surfaces = mapOf(
        "/api/v1/admin" to AdminScope.entries.map { "SCOPE_${it.scope}" }.toSet(),
        "/api/v1/client" to BuiltInClientScope.entries.map { "SCOPE_${it.scope}" }.toSet(),
        "/api/v1/flow" to setOf(HAS_STATE),
        "/api/oauth2" to setOf(IS_ANONYMOUS),
        "/api/openid" to setOf(IS_ANONYMOUS),
    )

    private val departures = listOf(
        Departure(
            "/api/v1/client/users/{userId}/claims", setOf(IS_CLIENT),
            "each claim's ACL decides what a client reads and writes there"
        ),
        Departure(
            "/api/v1/flow/providers/{providerId}/callback", setOf(IS_ANONYMOUS),
            "the provider redirects the browser there, so the state arrives as a parameter and is verified there"
        ),
        Departure(
            "/api/openid/userinfo", setOf(IS_USER),
            "OpenID Connect Core §5.3 has it called with an access token"
        ),
        Departure("/.well-known", setOf(IS_ANONYMOUS), "discovery is public by specification"),
        Departure("/openapi.yml", setOf(IS_ANONYMOUS), "the API description is public"),
        Departure("/version", setOf(IS_ANONYMOUS), "the version is public"),
        Departure("/flow", emptySet(), "it is a page, opened to anyone by the security configuration"),
        Departure("/admin", setOf(IS_ANONYMOUS), "it is a page, signing in on its own"),
        Departure("/swagger-ui", setOf(IS_ANONYMOUS), "it is a page, reading the public API description"),
    )

    private val routes: List<Route> by lazy {
        compiledClassesOf("com.sympauthy")
            .filter { it.isAnnotationPresent(Controller::class.java) }
            .flatMap(::routesOf)
    }

    @Test
    fun `Every route is gated as its surface admits, or as the departure listing it says`() {
        assertTrue(routes.isNotEmpty(), "no controller declares a route, so this test reads nothing")

        val misgated = routes.mapNotNull { route ->
            val departure = departures.firstOrNull { route.path.isUnder(it.path) }
            val admitted = surfaces.entries.firstOrNull { route.path.isUnder(it.key) }?.value
            when {
                departure != null -> if (route.gate == departure.gate) null else
                    "$route is listed as gated on ${departure.gate} because ${departure.reason}"
                admitted == null -> "$route is on no surface, and no departure lists it"
                route.gate.isNotEmpty() && admitted.containsAll(route.gate) -> null
                else -> "$route is not gated on one of its surface's $admitted"
            }
        }
        assertEquals(emptyList<String>(), misgated)
    }

    @Test
    fun `Every listed departure excuses a route`() {
        val unused = departures.filter { departure -> routes.none { it.path.isUnder(departure.path) } }
        assertEquals(emptyList<Departure>(), unused)
    }

    private fun routesOf(controller: Class<*>): List<Route> {
        val prefix = controller.getAnnotation(Controller::class.java).value.removeSuffix("/")
        val classGate = controller.getAnnotation(Secured::class.java)
        return controller.declaredMethods.flatMap { method ->
            val gate = (method.getAnnotation(Secured::class.java) ?: classGate)?.value?.toSet().orEmpty()
            mappingsOf(method).map { (httpMethod, path) ->
                Route("$httpMethod $prefix${path.removeSuffix("/")}", "$prefix$path", gate)
            }
        }
    }

    private fun mappingsOf(method: Method): List<Pair<String, String>> = method.annotations
        .filter { it.annotationClass.java.isAnnotationPresent(HttpMethodMapping::class.java) }
        .map { mapping ->
            val path = mapping.annotationClass.java.getMethod("value").invoke(mapping) as String
            mapping.annotationClass.java.simpleName.uppercase() to path
        }

    private fun String.isUnder(prefix: String) = this == prefix || startsWith("$prefix/")

    private data class Route(val operation: String, val path: String, val gate: Set<String>) {
        override fun toString() = "$operation (gated on $gate)"
    }

    private data class Departure(val path: String, val gate: Set<String>, val reason: String)
}
