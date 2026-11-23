package com.sympauthy.business.manager.lock

/**
 * A scheduled job one instance of this server doing it is enough for.
 *
 * Every value has a row in `job_leases`, seeded by the migration that created the table;
 * `JobLeaseRepositoryTest` holds the two sets equal, because a job whose row is missing takes no lease
 * and therefore never runs at all. Adding a value here means adding its row in the same change.
 */
enum class LeasedJob {

    CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS,

    CLEAN_ABANDONED_ACCOUNTS,

    CLEAN_EXPIRED_USER_SECURITY_CONTEXTS,

    /**
     * Deleting the tokens and codes that are done with, and then the grants left with neither — see
     * `docs/design/grants.md`.
     */
    CLEAN_FINISHED_GRANTS;

    /** The `job_leases` row this job takes, spelled the way the migration seeded it. */
    val leaseName: String
        get() = name.lowercase()
}
