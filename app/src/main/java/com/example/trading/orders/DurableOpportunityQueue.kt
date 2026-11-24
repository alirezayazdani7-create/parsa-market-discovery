package com.example.trading.orders

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** Durable representation of an order that currently blocks same-symbol handoff. */
@Entity(tableName = "open_order_occupancy")
data class OpenOrderOccupancyEntity(
    @PrimaryKey val orderId: String,
    val normalizedSymbol: String
)

/** Durable opportunity row; decimal market values are not stored as floating point. */
@Entity(tableName = "durable_opportunities")
data class DurableOpportunityEntity(
    @PrimaryKey val opportunityId: String,
    val symbol: String,
    val direction: String,
    val score: Int,
    val detectedAtMillis: Long,
    val expiresAtMillis: Long,
    val status: String
) {
    fun toRecord() = OpportunityRecord(
        TradeOpportunity(opportunityId, symbol, OpportunityDirection.valueOf(direction),
            score, detectedAtMillis, expiresAtMillis),
        OpportunityStatus.valueOf(status)
    )

    companion object {
        fun from(value: TradeOpportunity, status: OpportunityStatus) = DurableOpportunityEntity(
            value.opportunityId, value.symbol, value.direction.name, value.score,
            value.detectedAtMillis, value.expiresAtMillis, status.name
        )
    }
}

@Dao
abstract class DurableOpportunityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOpenOrder(value: OpenOrderOccupancyEntity): Long

    @Query("DELETE FROM open_order_occupancy WHERE orderId = :orderId AND normalizedSymbol = :symbol")
    abstract suspend fun deleteOpenOrder(orderId: String, symbol: String): Int

    @Query("SELECT COUNT(*) FROM open_order_occupancy WHERE normalizedSymbol = :symbol")
    abstract suspend fun countOpenOrders(symbol: String): Int

    @Query("SELECT * FROM open_order_occupancy ORDER BY normalizedSymbol, orderId")
    abstract suspend fun readOpenOrders(): List<OpenOrderOccupancyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOpportunity(value: DurableOpportunityEntity): Long

    @Query("SELECT * FROM durable_opportunities WHERE opportunityId = :id LIMIT 1")
    abstract suspend fun findOpportunity(id: String): DurableOpportunityEntity?

    @Query("SELECT * FROM durable_opportunities WHERE status = :status AND symbol = :symbol ORDER BY detectedAtMillis")
    abstract suspend fun findByStatusAndSymbol(status: String, symbol: String): List<DurableOpportunityEntity>

    @Query("SELECT * FROM durable_opportunities WHERE status = :status ORDER BY score DESC, detectedAtMillis DESC, opportunityId ASC LIMIT 1")
    abstract suspend fun nextReady(status: String): DurableOpportunityEntity?

    @Query("UPDATE durable_opportunities SET status = :status WHERE opportunityId = :id")
    abstract suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE durable_opportunities SET status = :expired WHERE status = :ready AND expiresAtMillis <= :now")
    abstract suspend fun expireReady(ready: String, expired: String, now: Long)

    @Query("SELECT * FROM durable_opportunities ORDER BY detectedAtMillis, opportunityId")
    abstract suspend fun readAllOpportunities(): List<DurableOpportunityEntity>

    @Query("SELECT COUNT(*) FROM durable_opportunities")
    abstract suspend fun opportunityCount(): Int

    @Query("DELETE FROM open_order_occupancy")
    abstract suspend fun clearOpenOrders()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOpenOrders(values: List<OpenOrderOccupancyEntity>)

    @Transaction
    open suspend fun offer(value: TradeOpportunity, now: Long): OpportunityOfferResult {
        val invalid = when {
            value.opportunityId.isBlank() -> "opportunityId must be non-empty"
            value.symbol.isBlank() -> "symbol must be non-empty"
            value.score !in 1..10 -> "score must be between 1 and 10"
            value.detectedAtMillis < 0L -> "detectedAtMillis must be non-negative"
            value.expiresAtMillis <= value.detectedAtMillis -> "expiresAtMillis must be after detectedAtMillis"
            else -> null
        }
        if (invalid != null) return OpportunityOfferResult(false, false, null, invalid)
        val existing = findOpportunity(value.opportunityId)
        if (existing != null) {
            val same = existing.symbol == value.symbol &&
                existing.direction == value.direction.name &&
                existing.score == value.score &&
                existing.detectedAtMillis == value.detectedAtMillis &&
                existing.expiresAtMillis == value.expiresAtMillis
            return if (same) OpportunityOfferResult(true, true, existing.toRecord())
            else OpportunityOfferResult(false, false, existing.toRecord(), "opportunityId was reused with a different payload")
        }
        val symbol = value.symbol.trim().uppercase()
        val status = when {
            now >= value.expiresAtMillis -> OpportunityStatus.EXPIRED
            countOpenOrders(symbol) > 0 -> OpportunityStatus.WAITING_FOR_ORDER_CLOSE
            else -> OpportunityStatus.READY_FOR_HUNTER
        }
        insertOpportunity(DurableOpportunityEntity.from(value, status))
        return OpportunityOfferResult(true, false, DurableOpportunityEntity.from(value, status).toRecord())
    }

    @Transaction
    open suspend fun closeOrder(orderId: String, symbol: String, closedAt: Long): List<TradeOpportunity> {
        val normalized = symbol.trim().uppercase()
        deleteOpenOrder(orderId, normalized)
        if (countOpenOrders(normalized) > 0) return emptyList()
        val waiting = findByStatusAndSymbol(OpportunityStatus.WAITING_FOR_ORDER_CLOSE.name, normalized)
        val released = mutableListOf<TradeOpportunity>()
        waiting.forEach { row ->
            val next = if (closedAt >= row.expiresAtMillis) OpportunityStatus.EXPIRED else OpportunityStatus.READY_FOR_HUNTER
            updateStatus(row.opportunityId, next.name)
            if (next == OpportunityStatus.READY_FOR_HUNTER) released += row.toRecord().opportunity
        }
        return released.sortedWith(compareByDescending<TradeOpportunity> { it.score }
            .thenByDescending { it.detectedAtMillis }.thenBy { it.opportunityId })
    }

    @Transaction
    open suspend fun claim(direction: OpportunityDirection, now: Long): TradeOpportunity? {
        expireReady(OpportunityStatus.READY_FOR_HUNTER.name, OpportunityStatus.EXPIRED.name, now)
        val next = nextReady(OpportunityStatus.READY_FOR_HUNTER.name) ?: return null
        if (next.direction != direction.name) {
            val candidates = readAllOpportunities()
                .filter { it.status == OpportunityStatus.READY_FOR_HUNTER.name && it.direction == direction.name }
                .sortedWith(compareByDescending<DurableOpportunityEntity> { it.score }
                    .thenByDescending { it.detectedAtMillis }.thenBy { it.opportunityId })
            val selected = candidates.firstOrNull() ?: return null
            updateStatus(selected.opportunityId, OpportunityStatus.CLAIMED.name)
            return selected.toRecord().opportunity
        }
        updateStatus(next.opportunityId, OpportunityStatus.CLAIMED.name)
        return next.toRecord().opportunity
    }

    @Transaction
    open suspend fun restoreOpenOrders(values: List<OpenOrderOccupancyEntity>) {
        clearOpenOrders()
        insertOpenOrders(values.distinctBy { it.orderId })
    }
}

@Database(
    entities = [OpenOrderOccupancyEntity::class, DurableOpportunityEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DurableOpportunityDatabase : RoomDatabase() {
    abstract fun durableOpportunityDao(): DurableOpportunityDao

    companion object {
        fun open(context: Context): DurableOpportunityDatabase =
            Room.databaseBuilder(context.applicationContext, DurableOpportunityDatabase::class.java,
                "parsa_durable_opportunity_queue.db").build()
    }
}

/**
 * Persistence boundary for the Stage 3 queue contract.
 * Call restoreOpenOrders with the authoritative open-order snapshot before starting the scanner.
 */
class PersistentOpportunityQueue(private val dao: DurableOpportunityDao) {
    suspend fun restoreOpenOrders(openOrders: List<Pair<String, String>>) {
        require(openOrders.all { it.first.isNotBlank() && it.second.isNotBlank() })
        dao.restoreOpenOrders(openOrders.map { OpenOrderOccupancyEntity(it.first, it.second.trim().uppercase()) })
    }

    suspend fun onOrderOpened(orderId: String, symbol: String) {
        require(orderId.isNotBlank() && symbol.isNotBlank())
        dao.insertOpenOrder(OpenOrderOccupancyEntity(orderId, symbol.trim().uppercase()))
    }

    suspend fun onOrderClosed(orderId: String, symbol: String, closedAtMillis: Long): List<TradeOpportunity> {
        require(orderId.isNotBlank() && symbol.isNotBlank())
        return dao.closeOrder(orderId, symbol, closedAtMillis)
    }

    suspend fun offer(opportunity: TradeOpportunity, nowMillis: Long): OpportunityOfferResult =
        dao.offer(opportunity, nowMillis)

    suspend fun claimNext(direction: OpportunityDirection, nowMillis: Long): TradeOpportunity? =
        dao.claim(direction, nowMillis)

    suspend fun snapshot(): List<OpportunityRecord> = dao.readAllOpportunities().map { it.toRecord() }

    suspend fun openOrderSnapshot(): List<Pair<String, String>> =
        dao.readOpenOrders().map { it.orderId to it.normalizedSymbol }
}
