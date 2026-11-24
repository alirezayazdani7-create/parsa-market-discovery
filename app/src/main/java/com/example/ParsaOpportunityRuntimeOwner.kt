package com.example

import android.content.Context
import com.example.trading.orders.BinancePublicMarketDataSource
import com.example.trading.orders.ConfirmedOrderEventDatabase
import com.example.trading.orders.DurableOpportunityDatabase
import com.example.trading.orders.FreshDataHunterReviewGateway
import com.example.trading.orders.LocalConfirmedOrderLifecycleEventSource
import com.example.trading.orders.OpportunityRuntimeLifecycleObserver
import com.example.trading.orders.PersistentOpportunityHandoff
import com.example.trading.orders.PersistentOpportunityQueue
import com.example.trading.orders.ReadOnlyContinuousMarketScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Owns the explicitly read-only opportunity runtime for one Activity lifecycle.
 * It has public market-data GET access only and no order/account execution client.
 */
class ParsaOpportunityRuntimeOwner(context: Context) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val opportunityDatabase = DurableOpportunityDatabase.open(context)
    private val orderEventDatabase = ConfirmedOrderEventDatabase.open(context)
    private val eventSource = LocalConfirmedOrderLifecycleEventSource(orderEventDatabase.confirmedOrderEventDao())
    private val marketData = BinancePublicMarketDataSource()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val scanner = ReadOnlyContinuousMarketScanner(
        source = marketData,
        symbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT"),
        interval = "5m",
        pollEveryMillis = TimeUnit.SECONDS.toMillis(30),
        initialDelayMillis = 0L,
        scheduler = scheduler,
        onStatus = { /* Status is intentionally not surfaced as a trading signal. */ }
    )
    private val runtime = PersistentOpportunityHandoff(
        scanner = scanner,
        hunters = FreshDataHunterReviewGateway(marketData, interval = "5m"),
        queue = PersistentOpportunityQueue(opportunityDatabase.durableOpportunityDao()),
        orderEvents = eventSource,
        scope = scope,
        onHandoff = { /* Review-only result; no order execution path exists. */ }
    )

    val lifecycleObserver = OpportunityRuntimeLifecycleObserver(runtime)

    override fun close() {
        runtime.stop()
        scanner.shutdown()
        scope.cancel()
        opportunityDatabase.close()
        orderEventDatabase.close()
    }
}
