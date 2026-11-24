package com.example.trading.orders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalConfirmedOrderLifecycleEventSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "confirmed-order-events-test.db"
    private lateinit var database: ConfirmedOrderEventDatabase
    private lateinit var source: LocalConfirmedOrderLifecycleEventSource

    private fun open() {
        database = Room.databaseBuilder(context, ConfirmedOrderEventDatabase::class.java, name)
            .allowMainThreadQueries().build()
        source = LocalConfirmedOrderLifecycleEventSource(database.confirmedOrderEventDao())
    }

    @Before fun setUp() {
        context.deleteDatabase(name)
        open()
    }

    @After fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(name)
    }

    @Test fun confirmedOpenAndCloseArePersistedAndPublishedOnce() = runBlocking {
        val notices = mutableListOf<ConfirmedOrderLifecycleNotice>()
        val attached = source.subscribeAndLoadOpenOrders(notices::add)
        assertTrue(attached.initialOpenOrders.isEmpty())

        source.recordConfirmedOpen("o-1", "BTC/USDT", 100L, "evt-open")
        assertEquals(1, notices.size)
        assertTrue(notices.single().isOpen)

        source.recordConfirmedOpen("o-1", "BTC/USDT", 100L, "evt-open")
        assertEquals("duplicate event must not be published twice", 1, notices.size)

        source.recordConfirmedClose("o-1", "BTC/USDT", 200L, "evt-close")
        assertEquals(2, notices.size)
        assertFalse(notices.last().isOpen)
        attached.subscription.close()

        val reopened = source.subscribeAndLoadOpenOrders {}
        assertTrue(reopened.initialOpenOrders.isEmpty())
        reopened.subscription.close()
    }

    @Test fun openOrderSnapshotSurvivesDatabaseReopen() = runBlocking {
        source.recordConfirmedOpen("o-2", "ETH/USDT", 100L, "evt-2")
        database.close()
        open()
        val attached = source.subscribeAndLoadOpenOrders {}
        assertEquals(listOf("o-2" to "ETH/USDT"), attached.initialOpenOrders)
        attached.subscription.close()
    }

    @Test fun snapshotAndSubscriptionHaveNoLostNoticeWindow() = runBlocking {
        source.restoreAuthoritativeSnapshot(listOf("o-3" to "SOL/USDT"))
        val received = mutableListOf<ConfirmedOrderLifecycleNotice>()
        val attached = source.subscribeAndLoadOpenOrders(received::add)
        source.recordConfirmedClose("o-3", "SOL/USDT", 300L, "evt-3")
        assertEquals(1, received.size)
        assertFalse(received.single().isOpen)
        attached.subscription.close()
    }
}
