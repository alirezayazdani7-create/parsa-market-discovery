package com.example.trading.orders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderEventLedgerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "ledger-test-${System.nanoTime()}.db"
    private var database: OrderEventLedgerDatabase? = null

    private fun openDatabase(): OrderEventLedgerDatabase =
        Room.databaseBuilder(context, OrderEventLedgerDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun event(
        id: String,
        type: OrderEventType = OrderEventType.FILL_RECEIVED,
        fillId: String? = "fill-$id",
        quantity: String? = "0.10"
    ) = OrderEvent(
        eventId = id,
        orderId = "order-1",
        type = type,
        eventTimeMillis = 100L,
        fillId = fillId,
        fillQuantity = quantity?.let(::BigDecimal)
    )

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun appendPersistsExactDecimalPayloadAndSequence() = runBlocking {
        val dao = openDatabase().orderEventLedgerDao()
        val first = dao.append(OrderEventLedgerEntry.from(event("event-1")))
        val second = dao.append(OrderEventLedgerEntry.from(event("event-2", quantity = "0.20")))

        assertEquals(LedgerAppendStatus.INSERTED, first.status)
        assertEquals(LedgerAppendStatus.INSERTED, second.status)
        assertTrue(first.sequence!! < second.sequence!!)
        val rows = dao.readOrderEvents("order-1")
        assertEquals(listOf("0.1", "0.2"), rows.map { it.fillQuantity })
    }

    @Test fun sameEventAndPayloadIsDuplicateWithoutExtraRow() = runBlocking {
        val dao = openDatabase().orderEventLedgerDao()
        val entry = OrderEventLedgerEntry.from(event("event-1"))
        assertEquals(LedgerAppendStatus.INSERTED, dao.append(entry).status)
        val replay = dao.append(entry.copy(sequence = 0))
        assertEquals(LedgerAppendStatus.DUPLICATE, replay.status)
        assertEquals(1, dao.countAll())
    }

    @Test fun sameEventIdWithDifferentPayloadIsConflictAndPreservesOriginal() = runBlocking {
        val dao = openDatabase().orderEventLedgerDao()
        val original = OrderEventLedgerEntry.from(event("event-1"))
        dao.append(original)
        val conflict = dao.append(OrderEventLedgerEntry.from(event("event-1", quantity = "0.11")))
        assertEquals(LedgerAppendStatus.CONFLICT, conflict.status)
        assertEquals(1, dao.countAll())
        assertEquals("0.1", dao.find("order-1", "event-1")?.fillQuantity)
    }

    @Test fun ledgerSurvivesDatabaseCloseAndReopen() = runBlocking {
        val firstDao = openDatabase().orderEventLedgerDao()
        firstDao.append(OrderEventLedgerEntry.from(event("event-durable")))
        database?.close()
        database = null

        val reopenedDao = openDatabase().orderEventLedgerDao()
        val restored = reopenedDao.readOrderEvents("order-1")
        assertEquals(1, restored.size)
        assertEquals("event-durable", restored.single().eventId)
        assertEquals("0.1", restored.single().fillQuantity)
    }

    @Test fun sameEventIdOnDifferentOrdersIsAllowed() = runBlocking {
        val dao = openDatabase().orderEventLedgerDao()
        val first = OrderEventLedgerEntry.from(event("shared-id")).copy(orderId = "order-A")
        val second = OrderEventLedgerEntry.from(event("shared-id")).copy(orderId = "order-B")
        assertEquals(LedgerAppendStatus.INSERTED, dao.append(first).status)
        assertEquals(LedgerAppendStatus.INSERTED, dao.append(second).status)
        assertEquals(2, dao.countAll())
    }

    @Test fun invalidIdentifiersAreRejectedBeforeInsert() = runBlocking {
        val dao = openDatabase().orderEventLedgerDao()
        val invalid = OrderEventLedgerEntry.from(event("event-1")).copy(orderId = " ")
        try {
            dao.append(invalid)
            throw AssertionError("blank order ID must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, dao.countAll())
        }
    }
}
