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

/**
 * Offline component integration: lifecycle transition -> durable event ledger ->
 * Paper position -> close/accounting -> ledger replay reconciliation.
 * It deliberately uses deterministic fixtures and no exchange/network adapter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflinePaperLifecycleIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "paper-integration-${System.nanoTime()}.db"
    private var database: OrderEventLedgerDatabase? = null
    private fun d(value: String) = BigDecimal(value)

    private fun openDao(): OrderEventLedgerDao =
        Room.databaseBuilder(context, OrderEventLedgerDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
            .orderEventLedgerDao()

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun filledLifecycleIsPersistedAndReconcilesToPaperNetPnl() = runBlocking {
        val dao = openDao()
        val lifecycle = OrderLifecycle()
        var state = OrderState("paper-order-1", "paper-client-1", d("2"))

        val submit = OrderEvent("evt-submit", state.orderId, OrderEventType.SUBMIT_SENT, 100L)
        val submitResult = lifecycle.apply(state, submit)
        assertTrue(submitResult.accepted)
        state = submitResult.state
        assertEquals(LedgerAppendStatus.INSERTED, dao.append(OrderEventLedgerEntry.from(submit)).status)

        val fill = OrderEvent(
            eventId = "evt-fill",
            orderId = state.orderId,
            type = OrderEventType.FILL_RECEIVED,
            eventTimeMillis = 101L,
            fillId = "paper-fill-1",
            fillQuantity = d("2")
        )
        val fillResult = lifecycle.apply(state, fill)
        assertTrue(fillResult.accepted)
        state = fillResult.state
        assertEquals(OrderStatus.FILLED, state.status)
        assertEquals(0, state.cumulativeFilledQuantity.compareTo(d("2")))
        assertEquals(LedgerAppendStatus.INSERTED, dao.append(OrderEventLedgerEntry.from(fill)).status)

        val engine = OfflinePaperEngine()
        val entry = PaperSignal(
            signalId = "paper-position-1",
            symbol = "BTCUSDT",
            side = PaperSide.LONG,
            score = 9,
            generatedAtMillis = 100L,
            expiresAtMillis = 200L,
            referenceEntryPrice = d("100"),
            quantity = state.cumulativeFilledQuantity,
            leverage = 5
        )
        val opened = engine.submit(entry, nowMillis = 101L)
        assertEquals(PaperEntryStatus.ACCEPTED, opened.status)
        val closed = engine.close("paper-position-1", d("101"), 102L)
        assertEquals(PaperCloseStatus.CLOSED, closed.status)
        val trade = closed.trade!!
        assertEquals(0, trade.rawPricePnl.compareTo(d("2")))
        assertEquals(0, trade.totalFees.compareTo(d("0.03618")))
        assertEquals(0, trade.netPnl.compareTo(d("1.96382")))

        // Same exchange-style fill event replay: state and persistent ledger must not double count.
        val replay = lifecycle.apply(state, fill)
        assertTrue(replay.accepted)
        assertTrue(replay.duplicate)
        assertEquals(0, replay.state.cumulativeFilledQuantity.compareTo(d("2")))
        assertEquals(LedgerAppendStatus.DUPLICATE, dao.append(OrderEventLedgerEntry.from(fill)).status)
        assertEquals(2, dao.countAll())

        database?.close()
        database = null
        val reopened = openDao()
        assertEquals(2, reopened.countAll())
        assertEquals(listOf("evt-submit", "evt-fill"), reopened.readOrderEvents("paper-order-1").map { it.eventId })
    }
}
