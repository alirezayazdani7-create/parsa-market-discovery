package com.example.trading.orders

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "order_event_ledger",
    indices = [Index(value = ["orderId", "eventId"], unique = true)]
)
data class OrderEventLedgerEntry(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val orderId: String,
    val eventId: String,
    val eventType: String,
    val eventTimeMillis: Long,
    val fillId: String?,
    val fillQuantity: String?,
    val observedStatus: String?,
    val observedCumulativeFilledQuantity: String?,
    val reason: String?,
    val payloadFingerprint: String
) {
    companion object {
        fun from(event: OrderEvent): OrderEventLedgerEntry = OrderEventLedgerEntry(
            orderId = event.orderId,
            eventId = event.eventId,
            eventType = event.type.name,
            eventTimeMillis = event.eventTimeMillis,
            fillId = event.fillId,
            fillQuantity = event.fillQuantity?.stripTrailingZeros()?.toPlainString(),
            observedStatus = event.observedStatus?.name,
            observedCumulativeFilledQuantity = event.observedCumulativeFilledQuantity
                ?.stripTrailingZeros()?.toPlainString(),
            reason = event.reason,
            payloadFingerprint = event.canonicalFingerprint()
        )
    }
}

enum class LedgerAppendStatus { INSERTED, DUPLICATE, CONFLICT }

data class LedgerAppendResult(
    val status: LedgerAppendStatus,
    val sequence: Long? = null
)

@Dao
abstract class OrderEventLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnoringDuplicate(entry: OrderEventLedgerEntry): Long

    @Query("SELECT * FROM order_event_ledger WHERE orderId = :orderId AND eventId = :eventId LIMIT 1")
    abstract suspend fun find(orderId: String, eventId: String): OrderEventLedgerEntry?

    @Query("SELECT * FROM order_event_ledger WHERE orderId = :orderId ORDER BY sequence ASC")
    abstract suspend fun readOrderEvents(orderId: String): List<OrderEventLedgerEntry>

    @Query("SELECT COUNT(*) FROM order_event_ledger")
    abstract suspend fun countAll(): Int

    @Transaction
    open suspend fun append(entry: OrderEventLedgerEntry): LedgerAppendResult {
        require(entry.orderId.isNotBlank()) { "orderId must be non-empty" }
        require(entry.eventId.isNotBlank()) { "eventId must be non-empty" }
        require(entry.payloadFingerprint.isNotEmpty()) { "payloadFingerprint must be non-empty" }

        val insertedSequence = insertIgnoringDuplicate(entry)
        if (insertedSequence != -1L) {
            return LedgerAppendResult(LedgerAppendStatus.INSERTED, insertedSequence)
        }

        val existing = find(entry.orderId, entry.eventId)
            ?: error("Unique event conflict occurred but the existing row was not found")
        return if (existing.payloadFingerprint == entry.payloadFingerprint) {
            LedgerAppendResult(LedgerAppendStatus.DUPLICATE, existing.sequence)
        } else {
            LedgerAppendResult(LedgerAppendStatus.CONFLICT, existing.sequence)
        }
    }
}

@Database(entities = [OrderEventLedgerEntry::class], version = 1, exportSchema = false)
abstract class OrderEventLedgerDatabase : RoomDatabase() {
    abstract fun orderEventLedgerDao(): OrderEventLedgerDao

    companion object {
        fun open(context: Context): OrderEventLedgerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OrderEventLedgerDatabase::class.java,
                "parsa_order_event_ledger.db"
            ).build()
    }
}
