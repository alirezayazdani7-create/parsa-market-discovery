package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.universe.MarketUniverseManager
import com.example.trading.orders.BinancePublicMarketDataSource
import com.example.trading.orders.DurableOpportunityDatabase
import com.example.trading.orders.FreshDataHunterReviewGateway
import com.example.trading.orders.OpportunityRuntimeLifecycleObserver
import com.example.trading.orders.PersistentOpportunityHandoff
import com.example.trading.orders.PersistentOpportunityQueue
import com.example.trading.orders.ReadOnlyContinuousMarketScanner
import com.example.trading.orders.RoomConfirmedOrderLifecycleEventSource
import com.example.ui.audit.AuditScreen
import com.example.ui.audit.AuditViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ParsaNavyDark

class MainActivity : ComponentActivity() {
  private val auditViewModel: AuditViewModel by viewModels()

  private var opportunityDatabase: DurableOpportunityDatabase? = null
  private var opportunityEventSource: RoomConfirmedOrderLifecycleEventSource? = null
  private var opportunityScanner: ReadOnlyContinuousMarketScanner? = null
  private var opportunityRuntime: PersistentOpportunityHandoff? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    initializeReadOnlyOpportunityRuntime()

    setContent {
      MyApplicationTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = ParsaNavyDark
        ) {
          AuditScreen(viewModel = auditViewModel)
        }
      }
    }
  }

  /**
   * Stage 6 app integration. This runtime performs public market-data GET requests only.
   * It has no exchange credentials, private endpoints, order placement or paper execution.
   */
  private fun initializeReadOnlyOpportunityRuntime() {
    val marketData = BinancePublicMarketDataSource()
    val database = DurableOpportunityDatabase.open(applicationContext)
    opportunityDatabase = database

    val queue = PersistentOpportunityQueue(database.durableOpportunityDao())
    val eventSource = RoomConfirmedOrderLifecycleEventSource(queue, lifecycleScope)
    opportunityEventSource = eventSource

    val symbols = MarketUniverseManager(AppDatabase.getDatabase(applicationContext))
      .getVerifiedCoreUniverse()
      .take(9)
      .map { it.symbol }

    val scanner = ReadOnlyContinuousMarketScanner(
      source = marketData,
      symbols = symbols,
      interval = "5m",
      pollEveryMillis = 15_000L,
      initialDelayMillis = 0L,
      onStatus = { /* Scanner status is intentionally non-blocking for the UI. */ }
    )
    opportunityScanner = scanner

    val runtime = PersistentOpportunityHandoff(
      scanner = scanner,
      hunters = FreshDataHunterReviewGateway(marketData, interval = "5m"),
      queue = queue,
      orderEvents = eventSource,
      scope = lifecycleScope,
      onStatus = { /* Runtime status is intentionally non-blocking for the UI. */ }
    )
    opportunityRuntime = runtime
    lifecycle.addObserver(OpportunityRuntimeLifecycleObserver(runtime))
  }

  override fun onDestroy() {
    opportunityRuntime?.stop()
    opportunityEventSource?.close()
    opportunityScanner?.shutdown()
    opportunityDatabase?.close()
    super.onDestroy()
  }
}
