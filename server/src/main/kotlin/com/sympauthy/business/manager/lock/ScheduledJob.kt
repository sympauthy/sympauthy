package com.sympauthy.business.manager.lock

/**
 * A job this server runs on a schedule, and that one instance at a time should be enough to run.
 *
 * Every value has a row in `job_leases`, seeded by the migration that created the table;
 * `JobLeaseRepositoryTest` holds the two sets equal, because a job whose row is missing takes no lease
 * and therefore never runs at all. Adding a value here means adding its row in the same change.
 *
 * A job carries no duration of its own. How long its lease stands is how long the instance holding it
 * may go silent, which is the same answer for every job and [JobLeaseManager.GRACE_PERIOD] gives it.
 */
enum class ScheduledJob {

    CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS,

    CLEAN_ABANDONED_ACCOUNTS;

    /** The `job_leases` row this job takes, spelled the way the migration seeded it. */
    val leaseName: String
        get() = name.lowercase()
}
