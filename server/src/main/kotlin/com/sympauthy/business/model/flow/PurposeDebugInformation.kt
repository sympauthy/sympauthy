package com.sympauthy.business.model.flow

import com.sympauthy.business.model.flow.PurposeDebugInformationType.DATE
import com.sympauthy.business.model.flow.PurposeDebugInformationType.PRESENCE
import com.sympauthy.business.model.flow.PurposeDebugInformationType.TEXT
import com.sympauthy.business.model.flow.PurposeDebugInformationType.TEXT_LIST
import java.time.LocalDateTime

/**
 * One thing a purpose has to say about where a session stands, written by the handler that owns the purpose
 * for an operator to read.
 *
 * A handler emits a list of these rather than a map: the order is the handler's, and it is the order the
 * operator reads in.
 *
 * **[code] is a key.** It is what an interface switches on to label and render the entry, so renaming or
 * removing one breaks a caller the way renaming a wire field does. The server writes no label beside it: the
 * interface already has to know a code to render it, and a label would be a second name for the entry.
 *
 * Each subtype is one [type], which is what lets an interface render a [code] it holds no label for yet.
 */
sealed class PurposeDebugInformation {
    /**
     * How [value] is rendered.
     */
    abstract val type: PurposeDebugInformationType

    /**
     * What this value is, in snake_case and unique within its purpose: `primary_credential_proven_at` for
     * `primaryCredentialProvenDate`. It names the field it came from closely enough to grep for, because the
     * operator reading it is on their way into the source.
     */
    abstract val code: String

    /**
     * The value as it stands, or null where the field exists and holds nothing. The code is what says the field
     * is there, so an unset value is published as an absent one rather than as an omitted entry — a field that
     * has gone from the list and a field that is empty are different things.
     */
    abstract val value: Any?

    data class Text(override val code: String, override val value: String?) : PurposeDebugInformation() {
        override val type get() = TEXT
    }

    data class TextList(override val code: String, override val value: List<String>?) : PurposeDebugInformation() {
        override val type get() = TEXT_LIST
    }

    data class Date(override val code: String, override val value: LocalDateTime?) : PurposeDebugInformation() {
        override val type get() = DATE
    }

    /**
     * Whether the session holds a value it does not publish. The handler emitting it says why the value itself
     * is withheld.
     */
    data class Presence(override val code: String, override val value: Boolean?) : PurposeDebugInformation() {
        override val type get() = PRESENCE
    }
}

/**
 * How the value of a [PurposeDebugInformation] is rendered.
 */
enum class PurposeDebugInformationType {
    TEXT,
    TEXT_LIST,
    DATE,
    PRESENCE
}
