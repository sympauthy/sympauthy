package com.sympauthy.business.model.flow

/**
 * What became of an [InteractiveFlowSession], as somebody reading the row after the fact asks it.
 *
 * **[EXPIRED] is evaluated last**, so it means *ongoing when the expiration passed* — abandoned, nobody
 * finished it — and no terminal session is ever relabelled by it. That is deliberately the inverse of the
 * order [com.sympauthy.business.mapper.InteractiveFlowSessionMapper.toInteractiveFlowSession] walks, which
 * puts expiry ahead of cancellation and completion; neither should be aligned to the other. The flow has to
 * route a person whose session ran out to the error page whatever else the row says, and a reader of the row
 * has to be told that nobody finished it. A completed session sits in the table until its expiration plus up
 * to a quarter of an hour of cron, so reading it through the flow's order would report most completed
 * sessions as failed.
 */
enum class InteractiveFlowSessionStatus {
    /** No terminal column is set and the expiration has not passed: somebody may still be walking it. */
    ONGOING,

    /** The session reached its completion, whether or not it has since expired. */
    COMPLETED,

    /** The end-user cancelled the session. */
    CANCELLED,

    /** The session failed, and the row carries the two message keys saying with what. */
    FAILED,

    /** The session was still ongoing when its expiration passed, and nothing has collected it yet. */
    EXPIRED
}
