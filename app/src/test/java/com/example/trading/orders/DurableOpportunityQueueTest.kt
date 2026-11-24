package com.example.trading.orders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurableOpportunityQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "durable-opportunity-test.db"
    private lateinit var database: DurableOpportunityDatabase
    private lateinit var queue: PersistentOpportunityQueue

    private fun openDatabase() {
        database = Room.databaseBuilder(context, DurableOpportunityDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        queue = PersistentOpportunityQueue(database.durableOpportunityDao())
    }

    @Before fun setUp() {
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    private fun opportunity(id: String = "btc-1", symbol: String = "BTC/USDT") =
        TradeOpportunity(id, symbol, OpportunityDirection.LONG, 8, 100L, 10_000L)

    @Test fun waitingOpportunityAndOpenOrderSurviveDatabaseReopen() = runBlocking {
        queue.restoreOpenOrders(listOf("order-1" to "BTC/USDT"))
        queue.offer(opportunity(), 200L)
        database.close()

        openDatabase()
        assertEquals(listOf("order-1" to "BTCUSDT"), queue.openOrderSnapshot())
        assertEquals(OpportunityStatus.WAITING_FOR_ORDER_CLOSE, queue.snapshot().single().status)
        assertNull(queue.claimNext(OpportunityDirection.LONG, 300L))

        queue.onOrderClosed("order-1", "BTC/USDT", 400L)
        assertEquals("btc-1", queue.claimNext(OpportunityDirection.LONG, 401L)?.opportunityId)
    }

    @Test fun duplicateOfferIsIdempotentAndConflictingReuseIsRejected() = runBlocking {
        assertTrue(queue.offer(opportunity(), 200L).accepted)
        assertTrue(queue.offer(opportunity(), 201L).duplicate)
        val conflict = queue.offer(opportunity().copy(score = 9), 202L)
        assertFalse(conflict.accepted)
        assertEquals(1, queue.snapshot().size)
    }

    @Test fun candidateWaitsUntilFinalSameSymbolOrderCloses() = runBlocking {
        queue.restoreOpenOrders(listOf("one" to "BTC/USDT", "two" to "BTC/USDT"))
        queue.offer(opportunity(), 200L)
        assertTrue(queue.onOrderClosed("one", "BTC/USDT", 300L).isEmpty())
        assertNull(queue.claimNext(OpportunityDirection.LONG, 301L))
        assertEquals(1, queue.onOrderClosed("two", "BTC/USDT", 400L).size)
        assertEquals("btc-1", queue.claimNext(OpportunityDirection.LONG, 401L)?.opportunityId)
    }

    @Test fun expiredCandidateIsNeverClaimed() = runBlocking {
        queue.offer(opportunity().copy(expiresAtMillis = 500L), 200L)
        assertNull(queue.claimNext(OpportunityDirection.LONG, 500L))
        assertEquals(OpportunityStatus.EXPIRED, queue.snapshot().single().status)
    }

    @Test fun claimedCandidateCannotBeClaimedAfterReopen() = runBlocking {
        queue.offer(opportunity(), 200L)
        assertNotNull(queue.claimNext(OpportunityDirection.LONG, 201L))
        database.close()
        openDatabase()
        assertNull(queue.claimNext(OpportunityDirection.LONG, 202L))
        assertEquals(OpportunityStatus.CLAIMED, queue.snapshot().single().status)
    }
}
