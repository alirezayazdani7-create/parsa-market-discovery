package com.example.trading.orders

/**
 * Deterministic in-memory handoff queue for opportunities discovered while an order is open.
 *
 * This class does not scan markets, submit orders, or execute trades. A separate scanner may
 * publish immutable candidates; an order lifecycle owner reports open/closed order identities.
 */
enum class OpportunityDirection { LONG, SHORT }
enum class OpportunityStatus { WAITING_FOR_ORDER_CLOSE, READY_FOR_HUNTER, CLAIMED, EXPIRED }

data class TradeOpportunity(
    val opportunityId: String,
    val symbol: String,
    val direction: OpportunityDirection,
    val score: Int,
    val detectedAtMillis: Long,
    val expiresAtMillis: Long
)

data class OpportunityRecord(
    val opportunity: TradeOpportunity,
    val status: OpportunityStatus
)

data class OpportunityOfferResult(
    val accepted: Boolean,
    val duplicate: Boolean,
    val record: OpportunityRecord?,
    val error: String? = null
)

/**
 * In-memory deterministic queue. Persistence and scanner integration are deliberately out
 * of scope; callers must restore open orders and candidates from their own durable source.
 */
class OpportunityQueue {
    private val openOrdersBySymbol = mutableMapOf<String, MutableSet<String>>()
    private val recordsById = linkedMapOf<String, OpportunityRecord>()

    @Synchronized
    fun onOrderOpened(orderId: String, symbol: String) {
        require(orderId.isNotBlank()) { "orderId must be non-empty" }
        require(symbol.isNotBlank()) { "symbol must be non-empty" }
        openOrdersBySymbol.getOrPut(normalizeSymbol(symbol)) { linkedSetOf() }.add(orderId)
    }

    /** Returns candidates released when this was the final open order for their symbol. */
    @Synchronized
    fun onOrderClosed(orderId: String, symbol: String, closedAtMillis: Long): List<TradeOpportunity> {
        require(orderId.isNotBlank()) { "orderId must be non-empty" }
        require(symbol.isNotBlank()) { "symbol must be non-empty" }
        val key = normalizeSymbol(symbol)
        val open = openOrdersBySymbol[key] ?: return emptyList()
        open.remove(orderId)
        if (open.isEmpty()) openOrdersBySymbol.remove(key)
        if (openOrdersBySymbol.containsKey(key)) return emptyList()

        val released = mutableListOf<TradeOpportunity>()
        recordsById.entries.toList().forEach { (id, record) ->
            if (normalizeSymbol(record.opportunity.symbol) == key &&
                record.status == OpportunityStatus.WAITING_FOR_ORDER_CLOSE) {
                val nextStatus = if (isExpired(record.opportunity, closedAtMillis)) {
                    OpportunityStatus.EXPIRED
                } else {
                    released += record.opportunity
                    OpportunityStatus.READY_FOR_HUNTER
                }
                recordsById[id] = record.copy(status = nextStatus)
            }
        }
        return released.sortedWith(priorityComparator)
    }

    @Synchronized
    fun offer(opportunity: TradeOpportunity, nowMillis: Long): OpportunityOfferResult {
        val validation = validate(opportunity)
        if (validation != null) return OpportunityOfferResult(false, false, null, validation)

        val existing = recordsById[opportunity.opportunityId]
        if (existing != null) {
            return if (existing.opportunity == opportunity) {
                OpportunityOfferResult(true, true, existing)
            } else {
                OpportunityOfferResult(false, false, existing, "opportunityId was reused with a different payload")
            }
        }

        val status = when {
            isExpired(opportunity, nowMillis) -> OpportunityStatus.EXPIRED
            openOrdersBySymbol.containsKey(normalizeSymbol(opportunity.symbol)) ->
                OpportunityStatus.WAITING_FOR_ORDER_CLOSE
            else -> OpportunityStatus.READY_FOR_HUNTER
        }
        val record = OpportunityRecord(opportunity, status)
        recordsById[opportunity.opportunityId] = record
        return OpportunityOfferResult(true, false, record)
    }

    /**
     * Claims one currently valid opportunity for the requested hunter.
     * The caller must independently re-run its analysis before any later decision.
     */
    @Synchronized
    fun claimNext(direction: OpportunityDirection, nowMillis: Long): TradeOpportunity? {
        expireReady(nowMillis)
        val selected = recordsById.values
            .filter { it.status == OpportunityStatus.READY_FOR_HUNTER && it.opportunity.direction == direction }
            .map { it.opportunity }
            .sortedWith(priorityComparator)
            .firstOrNull() ?: return null
        recordsById[selected.opportunityId] =
            recordsById.getValue(selected.opportunityId).copy(status = OpportunityStatus.CLAIMED)
        return selected
    }

    @Synchronized
    fun get(opportunityId: String): OpportunityRecord? = recordsById[opportunityId]

    @Synchronized
    fun snapshot(): List<OpportunityRecord> = recordsById.values.toList()

    private fun expireReady(nowMillis: Long) {
        recordsById.entries.toList().forEach { (id, record) ->
            if (record.status == OpportunityStatus.READY_FOR_HUNTER &&
                isExpired(record.opportunity, nowMillis)) {
                recordsById[id] = record.copy(status = OpportunityStatus.EXPIRED)
            }
        }
    }

    private fun validate(value: TradeOpportunity): String? = when {
        value.opportunityId.isBlank() -> "opportunityId must be non-empty"
        value.symbol.isBlank() -> "symbol must be non-empty"
        value.score !in 1..10 -> "score must be between 1 and 10"
        value.detectedAtMillis < 0L -> "detectedAtMillis must be non-negative"
        value.expiresAtMillis <= value.detectedAtMillis -> "expiresAtMillis must be after detectedAtMillis"
        else -> null
    }

    private fun isExpired(value: TradeOpportunity, nowMillis: Long) =
        nowMillis >= value.expiresAtMillis

    private fun normalizeSymbol(symbol: String) = symbol.trim().uppercase()

    private val priorityComparator =
        compareByDescending<TradeOpportunity> { it.score }
            .thenByDescending { it.detectedAtMillis }
            .thenBy { it.opportunityId }
}
