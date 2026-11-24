package com.example.trading.orders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-process adapter for confirmed order-state transitions produced by the app's order lifecycle
 * owner. It is deliberately not an exchange client and cannot create or execute orders.
 *
 * The source persists occupancy before publishing a notice. Snapshot registration and updates
 * share one mutex; each subscriber uses an unlimited channel so no event is lost while its
 * callback coroutine is being scheduled.
 */
class RoomConfirmedOrderLifecycleEventSource(
    private val queue: PersistentOpportunityQueue,
    private val scope: CoroutineScope
) : ConfirmedOrderLifecycleEventSource, AutoCloseable {
    private val mutex = Mutex()
    private val subscribers = linkedSetOf<Channel<ConfirmedOrderLifecycleNotice>>()
    private var closed = false

    override suspend fun subscribeAndLoadOpenOrders(
        listener: (ConfirmedOrderLifecycleNotice) -> Unit
    ): OrderEventSubscription = mutex.withLock {
        check(!closed) { "Order event source is closed" }
        val channel = Channel<ConfirmedOrderLifecycleNotice>(Channel.UNLIMITED)
        subscribers += channel
        val snapshot = queue.openOrderSnapshot()
        val job: Job = scope.launch {
            for (notice in channel) listener(notice)
        }
        OrderEventSubscription(snapshot, AutoCloseable {
            scope.launch {
                mutex.withLock {
                    subscribers.remove(channel)
                    channel.close()
                }
            }
            job.cancel()
        })
    }

    /** Call only after the local order state machine accepts an authoritative open transition. */
    suspend fun publishConfirmedOpen(orderId: String, symbol: String, observedAtMillis: Long) {
        require(orderId.isNotBlank() && symbol.isNotBlank() && observedAtMillis >= 0L)
        mutex.withLock {
            check(!closed) { "Order event source is closed" }
            queue.onOrderOpened(orderId, symbol)
            publishLocked(ConfirmedOrderLifecycleNotice(orderId, symbol, true, observedAtMillis))
        }
    }

    /** Call only after the local order state machine accepts a terminal close transition. */
    suspend fun publishConfirmedClose(orderId: String, symbol: String, observedAtMillis: Long) {
        require(orderId.isNotBlank() && symbol.isNotBlank() && observedAtMillis >= 0L)
        mutex.withLock {
            check(!closed) { "Order event source is closed" }
            queue.onOrderClosed(orderId, symbol, observedAtMillis)
            publishLocked(ConfirmedOrderLifecycleNotice(orderId, symbol, false, observedAtMillis))
        }
    }

    private fun publishLocked(notice: ConfirmedOrderLifecycleNotice) {
        subscribers.toList().forEach { channel ->
            check(channel.trySend(notice).isSuccess) { "Order event subscriber is unavailable" }
        }
    }

    override fun close() {
        scope.launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                subscribers.forEach { it.close() }
                subscribers.clear()
            }
        }
    }
}
