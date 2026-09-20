package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_sessions")
class InteractiveFlowSessionEntity(
    /**
     * Optimistic-concurrency counter, incremented by every guarded lifecycle update.
     * [com.sympauthy.data.repository.InteractiveFlowSessionRepository] carries why it is a plain column
     * rather than a Micronaut Data `@Version` property.
     */
    val version: Long = 0,

    val purposes: Array<String>,
    val initiatingPurpose: String,
    val sessionDate: LocalDateTime,
    val flowId: String? = null,
    /**
     * A second copy of `interactive_flow_session_oauth2.client_id` /
     * `interactive_flow_session_confirm.client_id`, written in the same transaction as the record it
     * duplicates and never written again. No foreign key: a client is configuration rather than a table,
     * so a live session may name one the configuration no longer declares.
     */
    val initiatingClientId: String? = null,
    val expirationDate: LocalDateTime,

    val userId: UUID? = null,
    val signedUp: Boolean = false,

    val mfaPassedDate: LocalDateTime? = null,

    /** Terminal redirect: where and how the end-user is handed back to the flow's initiator. */
    val successRedirectUri: String? = null,
    val redirectType: String? = null,
    val cancelRedirectUri: String? = null,

    val completedPurposes: Array<String> = emptyArray(),
    val completeDate: LocalDateTime? = null,

    val cancelDate: LocalDateTime? = null,

    val errorDate: LocalDateTime? = null,
    val errorDetailsId: String? = null,
    val errorDescriptionId: String? = null,
    @MappedProperty(type = DataType.JSON)
    val errorValues: Map<String, String>? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
