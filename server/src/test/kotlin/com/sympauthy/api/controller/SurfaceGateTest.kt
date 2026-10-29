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
 * Holds the gate of every route a controller declares to the surface its prefix names, and every route
 * gated otherwise, or on no surface, to the gate listed here for it.
 */
class SurfaceGateTest {

    private val surfaces = mapOf(
        "/api/v1/admin" to AdminScope.entries.map { "SCOPE_${it.scope}" }.toSet(),
        "/api/v1/client" to BuiltInClientScope.entries.map { "SCOPE_${it.scope}" }.toSet(),
        "/api/v1/flow" to setOf(HAS_STATE),
        "/api/oauth2" to setOf(IS_ANONYMOUS),
        "/api/openid" to setOf(IS_ANONYMOUS),
    )

    /**
     * The routes on a surface gated otherwise than it admits, each with its gate. Why is the documentation of
     * the controller declaring it.
     */
    private val departures = mapOf(
        "/api/v1/client/users/{userId}/claims" to setOf(IS_CLIENT),
        "/api/v1/flow/providers/{providerId}/callback" to setOf(IS_ANONYMOUS),
        "/api/openid/userinfo" to setOf(IS_USER),
    )

    private val outsideSurfaces = mapOf(
        "/.well-known/openid-configuration" to setOf(IS_ANONYMOUS),
        "/.well-known/public.jwk" to setOf(IS_ANONYMOUS),
        "/openapi.yml" to setOf(IS_ANONYMOUS),
        "/version" to setOf(IS_ANONYMOUS),
        "/flow/{path:[^\\.]*}" to emptySet(),
        "/admin" to setOf(IS_ANONYMOUS),
        "/admin/{path:[^\\.]*}" to setOf(IS_ANONYMOUS),
        "/swagger-ui" to setOf(IS_ANONYMOUS),
    )

    private val routes: List<Route> by lazy {
        compiledClassesOf("com.sympauthy")
            .filter { it.isAnnotationPresent(Controller::class.java) }
            .flatMap(::routesOf)
    }

    @Test
    fun `Every route is gated as its surface admits, or as the list naming it says`() {
        assertTrue(routes.isNotEmpty(), "no controller declares a route, so this test reads nothing")

        val misgated = routes.mapNotNull { route ->
            val departure = departures[route.path]
            val outside = outsideSurfaces[route.path]
            val admitted = surfaces.entries.firstOrNull { route.path.isUnder(it.key) }?.value
            when {
                departure != null -> if (route.gate == departure) null else
                    "$route is listed as a departure gated on $departure"
                admitted == null -> when (outside) {
                    null -> "$route is on no surface, and is not listed outside one"
                    route.gate -> null
                    else -> "$route is listed outside the surfaces gated on $outside"
                }
                route.gate.isNotEmpty() && admitted.containsAll(route.gate) -> null
                else -> "$route is not gated on one of its surface's $admitted"
            }
        }
        assertEquals(emptyList<String>(), misgated)
    }

    @Test
    fun `Every listed route is declared by a controller`() {
        val unused = (departures.keys + outsideSurfaces.keys).filter { path -> routes.none { it.path == path } }
        assertEquals(emptyList<String>(), unused)
    }

    private fun routesOf(controller: Class<*>): List<Route> {
        val prefix = controller.getAnnotation(Controller::class.java).value.removeSuffix("/")
        val classGate = controller.getAnnotation(Secured::class.java)
        return controller.declaredMethods.flatMap { method ->
            val gate = (method.getAnnotation(Secured::class.java) ?: classGate)?.value?.toSet().orEmpty()
            mappingsOf(method).map { (httpMethod, path) ->
                Route(httpMethod, "$prefix${path.removeSuffix("/")}".ifEmpty { "/" }, gate)
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

    private data class Route(val method: String, val path: String, val gate: Set<String>) {
        override fun toString() = "$method $path (gated on $gate)"
    }
}
