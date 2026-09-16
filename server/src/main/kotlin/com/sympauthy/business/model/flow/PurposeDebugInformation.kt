package com.sympauthy.business.model.flow

/**
 * One thing a purpose has to say about where a session stands, written by the handler that owns the purpose
 * for an operator to read.
 *
 * A handler emits a list of these rather than a map: the order is the handler's, and it is the order the
 * operator reads in. [value] is a string because it is read rather than parsed — a caller wanting structure
 * is asking for a different endpoint.
 *
 * **[displayName] is a label, not a key.** Nothing may switch on it — not a page rendering it, not a test
 * asserting a value — because a handler is free to reword its own label and that must not break anybody. It
 * is deliberately not localized: it is written at the site that knows the value, in the same English as the
 * KDoc beside it, and a bundle would put a translation layer between an operator and the thing they are
 * debugging. It names the field it came from closely enough to grep for, because the operator reading it is
 * on their way into the source.
 */
data class PurposeDebugInformation(
    /**
     * What this value is, phrased for a person: `Primary credential proven date` for
     * `primaryCredentialProvenDate`.
     */
    val displayName: String,

    /**
     * The value as it stands, or null where the field exists and holds nothing. The label is what says the
     * field is there, so an unset value is published as an absent one rather than as an omitted entry — a
     * field that has gone from the list and a field that is empty are different things.
     */
    val value: String?
)
