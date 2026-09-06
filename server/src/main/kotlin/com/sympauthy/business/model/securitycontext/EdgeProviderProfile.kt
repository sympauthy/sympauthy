package com.sympauthy.business.model.securitycontext

/**
 * How the proxy a deployment says sits in front of this server publishes what it saw of the caller.
 *
 * Naming one under `advanced.security-context.provider` is a promise that the server is reachable
 * through that proxy alone: its headers are then trusted as they arrive, and nothing here checks that
 * a request carrying them came from it. That check is a firewall rule or an origin lock, made once in
 * the deployment, where the topology is known — a list of addresses in YAML would be a second copy of
 * it, going stale without saying so.
 *
 * An implementation is an extraction rather than a table of header names, because two providers
 * publish fewer headers than they do fields: Google packs a country and a city into one header and
 * Akamai packs a dozen values into another. The rest are a header read, and are written as one.
 *
 * A field a provider does not publish is null, and so is a header that did not arrive. Nothing here
 * fails, which is why the condition a provider puts on its operator is documented on its own class:
 * an unmet one is silent, and what it leaves behind is either nothing or whatever the caller sent.
 *
 * **Supporting one more proxy is publishing one more `@Singleton` implementing this**, in
 * `business.manager.securitycontext.edge`. It is what a deployment may then name, what the
 * configuration is validated against and what the refusal lists, so there is nothing else to add it
 * to. An implementation takes nothing from the configuration, which is what lets the configuration be
 * built out of them.
 */
interface EdgeProviderProfile {

    /**
     * The name a deployment writes under `advanced.security-context.provider` to be read this way.
     *
     * It is the operator's word for their own world rather than this class's name shortened, and it
     * is unique across the implementations.
     */
    val name: String

    fun clientIp(request: ObservedRequest): String? = null

    fun country(request: ObservedRequest): String? = null

    fun region(request: ObservedRequest): String? = null

    fun city(request: ObservedRequest): String? = null

    /**
     * What this profile makes of [field] on [request], or null where the header it would read is
     * absent and where the provider publishes nothing for it.
     */
    fun read(field: SecurityContextField, request: ObservedRequest): String? = when (field) {
        SecurityContextField.CLIENT_IP -> clientIp(request)
        SecurityContextField.COUNTRY -> country(request)
        SecurityContextField.REGION -> region(request)
        SecurityContextField.CITY -> city(request)
    }
}
