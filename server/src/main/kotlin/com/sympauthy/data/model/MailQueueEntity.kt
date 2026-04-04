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
@MappedEntity("mail_queue")
class MailQueueEntity(
    val template: String,
    val locale: String,
    val receiver: String,
    val subjectKey: String,
    @MappedProperty(type = DataType.JSON)
    val parameters: Map<String, String>,
    val creationDate: LocalDateTime = LocalDateTime.now(),
    val expirationDate: LocalDateTime? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
