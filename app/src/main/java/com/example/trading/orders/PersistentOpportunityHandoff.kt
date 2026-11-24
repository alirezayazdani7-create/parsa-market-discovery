package com.example.trading.orders

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Confirmed order-state feed contract. Implementations must emit only authoritative open/close facts. */
data class ConfirmedOrderLifecycleNotice(
    val orderId: String,
    val symbol: String,
    val isOpen: Boolean,
    val observedAtMillis: Long
)

interface ConfirmedOrderLifecycleEventSource {
    suspend fun loadOpenOrders(): List<Pair<String, String>>
    fun subscribe(listener: (ConfirmedOrderLifecycleNotice) -> Unit): AutoCloseable
}

/**
 * App-owned orchestration for the durable queue, read-only scanner and fresh-data Hunters.
 * It cannot submit orders. The order event source is injected and must be authoritative.
 */
class PersistentOpportunityHandoff(
    private val scanner: ContinuousOpportunityScanner,
    private val hunters: HunterReviewGateway,
    private val queue: PersistentOpportunityQueue,
    private val orderEvents: ConfirmedOrderLifecycleEventSource,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onHandoff: (HandoffResult) -> Unit = {},
    private val onStatus: (String) -> Unit = {}
) {
    private val lock = Mutex()
    @Volatile private var started = false
    private var subscription: AutoCloseable? = null
    private var startupJob: Job? = null

    /** Restore authoritative open-order occupancy before the scanner can emit candidates. */
    fun start() {
        if (started) return
        started = true
        startupJob = scope.launch {
            try {
                lock.withLock {
                    val open = orderEvents.loadOpenOrders()
                    queue.restoreOpenOrders(open)
                    subscription = orderEvents.subscribe { notice ->
                        scope.launch { handleOrderNotice(notice) }
                    }
                }
                scanner.start { opportunity ->
                    scope.launch { handleOpportunity(opportunity) }
                }
                onStatus("RUNNING_READ_ONLY")
            } catch (error: Exception) {
                started = false
                subscription?.close()
                subscription = null
                onStatus("START_FAILED:${error.javaClass.simpleName}")
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        scanner.stop()
        subscription?.close()
        subscription = null
        startupJob?.cancel()
        startupJob = null
        onStatus("STOPPED")
    }

    fun isRunning(): Boolean = started

    private suspend fun handleOrderNotice(notice: ConfirmedOrderLifecycleNotice) {
        if (!notice.orderId.isNotBlank() || !notice.symbol.isNotBlank()) {
            onStatus("INVALID_ORDER_NOTICE")
            return
        }
        val handoffs = lock.withLock {
            if (notice.isOpen) {
                queue.onOrderOpened(notice.orderId, notice.symbol)
                emptyList()
            } else {
                queue.onOrderClosed(notice.orderId, notice.symbol, notice.observedAtMillis)
                claimReadyLocked(notice.observedAtMillis)
            }
        }
        deliver(handoffs)
    }

    private suspend fun handleOpportunity(opportunity: TradeOpportunity) {
        val handoffs = lock.withLock {
            val offered = queue.offer(opportunity, nowMillis())
            if (!offered.accepted) {
                onStatus("OPPORTUNITY_REJECTED:${offered.error}")
                emptyList()
            } else claimReadyLocked(nowMillis())
        }
        deliver(handoffs)
    }

    private suspend fun claimReadyLocked(time: Long): List<TradeOpportunity> {
        val claimed = mutableListOf<TradeOpportunity>()
        OpportunityDirection.values().forEach { direction ->
            while (true) {
                val next = queue.claimNext(direction, time) ?: break
                claimed += next
            }
        }
        return claimed
    }

    private fun deliver(opportunities: List<TradeOpportunity>) {
        opportunities.forEach { opportunity ->
            val review = try {
                when (opportunity.direction) {
                    OpportunityDirection.LONG -> hunters.reviewLong(opportunity)
                    OpportunityDirection.SHORT -> hunters.reviewShort(opportunity)
                }
            } catch (error: Exception) {
                HunterReviewResult(opportunity.opportunityId, opportunity.direction, false,
                    "HUNTER_REVIEW_FAILED:${error.javaClass.simpleName}")
            }
            onHandoff(HandoffResult(opportunity, review))
        }
    }
}

/** Binds explicit runtime start/stop to an Android lifecycle owner; no implicit execution path. */
class OpportunityRuntimeLifecycleObserver(
    private val runtime: PersistentOpportunityHandoff
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = runtime.start()
    override fun onStop(owner: LifecycleOwner) = runtime.stop()
}
