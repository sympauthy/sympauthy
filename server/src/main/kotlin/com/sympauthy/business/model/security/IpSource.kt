package com.sympauthy.business.model.security

/**
 * Which of the answers `SecurityContextUtil` takes gave [ObservedRequest.ipAddress] its value.
 *
 * **It is a source and not a `trusted` flag.** Naming a proxy is a promise an operator makes, and
 * `docs/security-context.md` states that nothing here verifies it — so a server answering "trusted" would be
 * asserting what it cannot know. This says what it actually did, and what that is worth belongs to
 * whoever reads it.
 *
 * The distinction exists for the sake of a caller acting on the address rather than recording it: a
 * throttle keyed on [SOCKET_PEER] would be keyed on one value the whole deployment shares.
 */
enum class IpSource {

    /**
     * A header a deployment named for itself under `advanced.security-context.ip.header`, read as it
     * stands.
     */
    NAMED_HEADER,

    /**
     * The edge a deployment named under `advanced.security-context.ip.provider`, which answered with
     * the address it says the request came from.
     */
    NAMED_EDGE,

    /**
     * The peer of the socket the request arrived on.
     *
     * It is what a deployment naming nothing has — the shipped default — what a named edge whose
     * header did not arrive falls back to, and what a configuration that did not parse reports. For a
     * deployment behind any proxy it is that proxy's own address, **identical for every caller**, so
     * it is not an address anybody may be singled out by.
     */
    SOCKET_PEER
}
