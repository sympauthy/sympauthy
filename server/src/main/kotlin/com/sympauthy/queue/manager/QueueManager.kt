package com.sympauthy.queue.manager

import com.sympauthy.data.currentConnectionOrNull
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.repository.QueueItemRepository
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime

/**
 * Where an item is written, together with the change it reports, for [QueueDelivery] to deliver once that change
 * has committed. What the queue promises is `docs/design/delivery.md`.
 */
@Singleton
class QueueManager(
    @Inject private val queueItemRepository: QueueItemRepository,
    @Inject private val queueDelivery: QueueDelivery
) : ApplicationEventListener<ServiceReadyEvent> {

    /**
     * Start the poll once the server is ready. It is started here rather than by [QueueDelivery], which already
     * listens for the server stopping and cannot implement the same listener for a second event.
     */
    override fun onApplicationEvent(event: ServiceReadyEvent) {
        queueDelivery.startPolling()
    }

    /**
     * Write an item of [kind] for [destination], carrying [payload], in the transaction the caller runs in, and have
     * this instance attempt it as soon as that transaction has committed.
     *
     * A caller writes it inside the transaction of the change the item reports: a change that rolls back takes its
     * item with it, and one that commits leaves the item to be delivered by whichever instance looks next, should
     * this one die before it attempts it. A caller outside a transaction commits the item on its own.
     *
     * The attempt is given when the transaction's connection closes, which it does once it has committed or rolled
     * back; after a rollback, it finds nothing.
     *
     * [maxAge] is how long the item stays worth delivering: an item whose next attempt would come later than that is
     * given up rather than reaching a recipient for whom it no longer means anything. A null [maxAge] never
     * expires, and is attempted until it is delivered.
     */
    suspend fun enqueue(destination: String, kind: String, payload: String, maxAge: Duration?) {
        val now = LocalDateTime.now()
        val entity = QueueItemEntity(
            destination = destination,
            kind = kind,
            payload = payload,
            creationDate = now,
            expirationDate = maxAge?.let { now.plus(it) }
        )
        val id = queueItemRepository.save(entity).id!!
        val connection = currentConnectionOrNull()
        if (connection == null) {
            queueDelivery.nudge(destination, id)
        } else {
            connection.registerSynchronization(object : ConnectionSynchronization {
                override fun afterClosed() {
                    queueDelivery.nudge(destination, id)
                }
            })
        }
    }
}
