package com.sympauthy.data.model

import java.util.*

/**
 * A row an interactive flow session writes provisionally: it does not count until that session
 * completes and clears [sessionId], and is collected if it never does. It takes its session id from
 * the user it belongs to, never from the session a request is serving. What the column means, and
 * which reads exclude such rows, is `docs/design/provisional-user.md`.
 *
 * ## There is no foreign key
 *
 * `interactive_flow_sessions.user_id` already references `users`, so a `users.session_id` foreign key
 * back to the session would close a cycle neither table could be written into first. The column is
 * therefore unenforced, as [com.sympauthy.data.model.GrantEntity.sessionId] already is.
 */
interface SessionScoped {

    /**
     * Identifier of the interactive flow session this row is provisional for, or null once it has been
     * promoted — which is to say, once it is a permanent row like any other.
     */
    val sessionId: UUID?
}
