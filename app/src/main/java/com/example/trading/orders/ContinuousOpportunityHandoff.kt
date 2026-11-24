package com.example.trading.orders

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapter contract for a continuously running opportunity scanner.
 *
 * Implementations may emit candidates while orders are open. This contract intentionally
 * contains no exchange client, scheduler, or market-data implementation.
 */
interface ContinuousOpportunityScanner {
    fun start(onOpportunity: (TradeOpportunity) -> Unit)
    fun stop()
}

/**
 * Review-only Hunter boundary. Implementations must fetch/re-check current market data and
 * return a review result. They must never submit or execute an order through this interface.
 */
interface HunterReviewGateway {
    fun reviewLong(opportunity: TradeOpportunity): HunterReviewResult
    fun reviewShort(opportunity: TradeOpportunity): HunterReviewResult
}

data class HunterReviewResult(
    val opportunityId: String,
    val direction: OpportunityDirection,
    val acceptedForFurtherAnalysis: Boolean,
    val reason: String? = null
)

data class HandoffResult(
    val opportunity: TradeOpportunity,
    val review: HunterReviewResult
)

/**
 * Connects a scanner callback, the Stage 3 queue, order lifecycle notifications, and
 * review-only Long/Short Hunter ports.
 *
 * A candidate found during an open same-symbol order remains queued. When the final order
 * closes, ready candidates are claimed once and immediately sent to the matching Hunter.
 * Candidates found when no same-symbol order is open are handed off immediately.
 *
 * This is an adapter/orchestration layer only: no market feed, order execution, or persistence.
 */
class ContinuousOpportunityHandoff(
    private val scanner: ContinuousOpportunityScanner,
    private val hunters: HunterReviewGateway,
    private val queue: OpportunityQueue = OpportunityQueue(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onHandoff: (HandoffResult) -> Unit = {}
) {
    private val started = AtomicBoolean(false)
    private val lock = Any()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            scanner.start(::onOpportunity)
        } catch (error: Exception) {
            started.set(false)
            throw error
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        scanner.stop()
    }

    fun isRunning(): Boolean = started.get()

    fun onOrderOpened(orderId: String, symbol: String) {
        synchronized(lock) {
            queue.onOrderOpened(orderId, symbol)
        }
    }

    fun onOrderClosed(orderId: String, symbol: String, closedAtMillis: Long = nowMillis()) {
        val claimed = synchronized(lock) {
            queue.onOrderClosed(orderId, symbol, closedAtMillis)
            claimAllReadyLocked(closedAtMillis)
        }
        deliver(claimed)
    }

    fun snapshot(): List<OpportunityRecord> = synchronized(lock) { queue.snapshot() }

    private fun onOpportunity(opportunity: TradeOpportunity) {
        val claimed = synchronized(lock) {
            queue.offer(opportunity, nowMillis())
            claimAllReadyLocked(nowMillis())
        }
        deliver(claimed)
    }

    /** Claims all currently ready candidates, preserving queue score/freshness priority. */
    private fun claimAllReadyLocked(time: Long): List<TradeOpportunity> {
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
            val result = try {
                when (opportunity.direction) {
                    OpportunityDirection.LONG -> hunters.reviewLong(opportunity)
                    OpportunityDirection.SHORT -> hunters.reviewShort(opportunity)
                }
            } catch (error: Exception) {
                HunterReviewResult(
                    opportunityId = opportunity.opportunityId,
                    direction = opportunity.direction,
                    acceptedForFurtherAnalysis = false,
                    reason = "Hunter review failed: ${error.message ?: error::class.java.simpleName}"
                )
            }
            onHandoff(HandoffResult(opportunity, result))
        }
    }
}
