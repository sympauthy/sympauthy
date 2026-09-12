package com.sympauthy.business.manager.lock

/**
 * A job one instance of this server doing it is enough for, whether it comes round on a schedule or
 * happens once at startup.
 *
 * Every value has a row in `job_leases`, seeded by the migration that created the table;
 * `JobLeaseRepositoryTest` holds the two sets equal, because a job whose row is missing takes no lease
 * and therefore never runs at all. Adding a value here means adding its row in the same change.
 *
 * A job carries no duration of its own. How long its lease stands is how long the instance holding it
 * may go silent, which is the same answer for every job and [JobLeaseManager.GRACE_PERIOD] gives it.
 */
enum class LeasedJob {

    CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS,

    CLEAN_ABANDONED_ACCOUNTS,

    /**
     * Delivering the mails a previous run of this server persisted and did not send, which happens once
     * when an instance becomes ready rather than on a schedule.
     */
    MAIL_BACKLOG;

    /** The `job_leases` row this job takes, spelled the way the migration seeded it. */
    val leaseName: String
        get() = name.lowercase()
}
