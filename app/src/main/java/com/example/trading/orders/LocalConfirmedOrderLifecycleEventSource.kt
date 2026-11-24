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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Entity(tableName = "confirmed_open_orders")
data class ConfirmedOpenOrderEntity(
    @PrimaryKey val orderId: String,
    val symbol: String
)

@Entity(tableName = "confirmed_order_notices")
data class ConfirmedOrderNoticeEntity(
    @PrimaryKey val eventId: String,
    val orderId: String,
    val symbol: String,
    val isOpen: Boolean,
    val observedAtMillis: Long
)

@Dao
abstract class ConfirmedOrderEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotice(notice: ConfirmedOrderNoticeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOpenOrder(order: ConfirmedOpenOrderEntity): Long

    @Query("DELETE FROM confirmed_open_orders WHERE orderId = :orderId AND symbol = :symbol")
    abstract suspend fun deleteOpenOrder(orderId: String, symbol: String): Int

    @Query("SELECT * FROM confirmed_open_orders ORDER BY symbol, orderId")
    abstract suspend fun readOpenOrders(): List<ConfirmedOpenOrderEntity>

    @Transaction
    open suspend fun applyNotice(notice: ConfirmedOrderNoticeEntity): Boolean {
        val inserted = insertNotice(notice)
        if (inserted == -1L) return false
        if (notice.isOpen) insertOpenOrder(ConfirmedOpenOrderEntity(notice.orderId, notice.symbol))
        else deleteOpenOrder(notice.orderId, notice.symbol)
        return true
    }

    @Transaction
    open suspend fun replaceOpenOrderSnapshot(values: List<ConfirmedOpenOrderEntity>) {
        clearOpenOrders()
        values.distinctBy { it.orderId }.forEach { insertOpenOrder(it) }
    }

    @Query("DELETE FROM confirmed_open_orders")
    abstract suspend fun clearOpenOrders()
}

@Database(
    entities = [ConfirmedOpenOrderEntity::class, ConfirmedOrderNoticeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ConfirmedOrderEventDatabase : RoomDatabase() {
    abstract fun confirmedOrderEventDao(): ConfirmedOrderEventDao

    companion object {
        fun open(context: Context): ConfirmedOrderEventDatabase =
            Room.databaseBuilder(context.applicationContext, ConfirmedOrderEventDatabase::class.java,
                "parsa_confirmed_order_events.db").build()
    }
}

/**
 * Durable local confirmed-order event source.
 * Only explicit confirmed lifecycle facts change the open-order snapshot.
 * A real exchange/account adapter may feed this store later; none is fabricated here.
 */
class LocalConfirmedOrderLifecycleEventSource(
    private val dao: ConfirmedOrderEventDao
) : ConfirmedOrderLifecycleEventSource {
    private val mutex = Mutex()
    private val listeners = CopyOnWriteArrayList<(ConfirmedOrderLifecycleNotice) -> Unit>()

    override suspend fun subscribeAndLoadOpenOrders(
        listener: (ConfirmedOrderLifecycleNotice) -> Unit
    ): OrderEventSubscription = mutex.withLock {
        val snapshot = dao.readOpenOrders().map { it.orderId to it.symbol }
        listeners += listener
        OrderEventSubscription(snapshot, AutoCloseable { listeners.remove(listener) })
    }

    suspend fun recordConfirmedOpen(orderId: String, symbol: String, observedAtMillis: Long,
                                    eventId: String = UUID.randomUUID().toString()) {
        record(ConfirmedOrderNoticeEntity(eventId, orderId, symbol.trim().uppercase(), true, observedAtMillis))
    }

    suspend fun recordConfirmedClose(orderId: String, symbol: String, observedAtMillis: Long,
                                     eventId: String = UUID.randomUUID().toString()) {
        record(ConfirmedOrderNoticeEntity(eventId, orderId, symbol.trim().uppercase(), false, observedAtMillis))
    }

    suspend fun restoreAuthoritativeSnapshot(openOrders: List<Pair<String, String>>) = mutex.withLock {
        require(openOrders.all { it.first.isNotBlank() && it.second.isNotBlank() })
        dao.replaceOpenOrderSnapshot(openOrders.map {
            ConfirmedOpenOrderEntity(it.first, it.second.trim().uppercase())
        })
    }

    private suspend fun record(notice: ConfirmedOrderNoticeEntity) = mutex.withLock {
        require(notice.orderId.isNotBlank() && notice.symbol.isNotBlank())
        val inserted = dao.applyNotice(notice)
        if (inserted) {
            val event = ConfirmedOrderLifecycleNotice(
                notice.orderId, notice.symbol, notice.isOpen, notice.observedAtMillis
            )
            listeners.toList().forEach { it(event) }
        }
    }
}
