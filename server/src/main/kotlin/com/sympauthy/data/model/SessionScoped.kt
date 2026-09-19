package com.sympauthy.data.model

import java.util.*

/**
 * A row an interactive flow session created and does not own permanently: it exists only for that
 * session until the session completes, and is collected with the session if it never does.
 *
 * Signing up spans many requests — and, with a third-party provider, a redirect out and back — so no
 * database transaction can wrap it. The account is written eagerly all the same, which leaves a real
 * but never-validated account behind whenever the person walks away. A nullable session id is how
 * that long-running work is made all-or-nothing without a transaction that could span it: the rows
 * are written provisionally, promoted in one transaction when the session completes, and garbage
 * collected by [com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner] when it expires.
 *
 * ## A row is provisional exactly when its user is
 *
 * Every row scoped this way belongs to a user, and takes its session id **from that user** rather
 * than from the session the request happens to be serving. That is what makes the two agree by
 * construction: a committed account can never grow a provisional row, and a provisional account can
 * never grow a committed one.
 *
 * The invariant is what lets a read keyed by a user id stay as it was. Such a read already trusts the
 * id it was handed, and the row it finds is exactly as visible as the user is. Only a query that
 * reaches a user **without holding its id** — a listing, an identifier lookup, a provider subject —
 * has to exclude the provisional rows, because it is the one that could hand a caller an account the
 * server has not finished creating.
 *
 * ## There is no foreign key
 *
 * `interactive_flow_sessions.user_id` already references `users`, so a `users.session_id` foreign key
 * back to the session would close a cycle neither table could be written into first. The column is
 * therefore unenforced, as
 * [com.sympauthy.data.model.AuthenticationTokenEntity.sessionId] already is.
 */
interface SessionScoped {

    /**
     * Identifier of the interactive flow session this row is provisional for, or null once it has been
     * promoted — which is to say, once it is a permanent row like any other.
     */
    val sessionId: UUID?
}
