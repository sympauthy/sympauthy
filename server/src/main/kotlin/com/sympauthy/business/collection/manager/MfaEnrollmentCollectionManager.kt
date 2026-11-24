package com.sympauthy.business.collection.manager

import com.sympauthy.business.collection.model.CollectionCapabilities
import com.sympauthy.business.collection.model.CollectionCriteria
import com.sympauthy.business.collection.model.CollectionFieldType.DATE_TIME
import com.sympauthy.business.collection.model.CollectionFieldType.UUID
import com.sympauthy.business.collection.model.CollectionFields
import com.sympauthy.business.collection.model.Page
import com.sympauthy.business.collection.model.PageParams
import com.sympauthy.business.collection.model.collectionFields
import com.sympauthy.business.mfa.manager.TotpManager
import com.sympauthy.business.mfa.model.TotpEnrollment
import com.sympauthy.business.user.model.User
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the multi-factor authentication methods a user has enrolled.
 */
@Singleton
class MfaEnrollmentCollectionManager(
    @Inject private val totpManager: TotpManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the enrollments the [user] has confirmed that
     * [criteria] keep.
     */
    suspend fun listConfirmedEnrollments(
        user: User,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<TotpEnrollment> = fields().page(
        items = totpManager.findConfirmedEnrollments(user.id),
        criteria = criteria,
        pageParams = pageParams
    )

    /**
     * The fields this collection offers, ending on the enrollment identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     *
     * The collection's own order is the confirmation rather than the enrollment: an enrollment is
     * listed once it is confirmed, so confirming is what appends to the collection, and ordering on
     * the enrollment date would insert one started last week into a page a caller has already
     * walked.
     */
    private suspend fun fields(): CollectionFields<TotpEnrollment> = collectionFields(
        uniqueKey = compareBy(TotpEnrollment::id)
    ) {
        field("id", UUID, key = "fields.mfa_enrollment_id", read = TotpEnrollment::id)
        field("creation_date", DATE_TIME, sortable = true, read = TotpEnrollment::creationDate)
        field("confirmed_date", DATE_TIME, sortable = true, read = TotpEnrollment::confirmedDate)
        defaultSort("confirmed_date")
    }
}
