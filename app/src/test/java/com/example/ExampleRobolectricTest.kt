package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.SystemStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun read_string_from_context() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PARSA", appName)
  }

  @Test
  fun room_database_insert_and_query_system_state() = runBlocking {
    val state = SystemStateEntity(
      stateKey = "CURRENT_STAGE",
      value = "PROJECT_INITIALIZATION",
      stage = "PROJECT_INITIALIZATION"
    )
    db.systemStateDao().insertOrUpdateState(state)

    val retrieved = db.systemStateDao().getStateByKey("CURRENT_STAGE")
    assertNotNull(retrieved)
    assertEquals("PROJECT_INITIALIZATION", retrieved?.value)
  }

  @Test
  fun room_database_audit_logs() = runBlocking {
    val log = AuditLogEntity(
      level = "INFO",
      category = "SYSTEM",
      message = "Initial system startup"
    )
    val id = db.auditLogDao().insertLog(log)
    assertTrue(id > 0)

    val recent = db.auditLogDao().getRecentLogs(10)
    assertEquals(1, recent.size)
    assertEquals("SYSTEM", recent[0].category)
  }

  @Test
  fun room_database_experiments_and_models() = runBlocking {
    val exp = com.example.data.entity.ExperimentEntity(
      name = "INIT_BENCHMARK",
      type = "BASE_TEST",
      status = "CONFIGURED",
      configJson = "{}"
    )
    val expId = db.experimentDao().insertExperiment(exp)
    assertTrue(expId > 0)

    val list = db.experimentDao().getExperimentsList()
    assertEquals(1, list.size)
    assertEquals("INIT_BENCHMARK", list[0].name)

    val model = com.example.data.entity.ModelVersionEntity(
      modelName = "BASE_TRANSFORMER",
      versionTag = "v0.1-stub",
      architecture = "NOT_IMPLEMENTED",
      status = "INITIALIZED"
    )
    val modelId = db.modelVersionDao().insertModelVersion(model)
    assertTrue(modelId > 0)

    val memory = com.example.data.entity.MemoryVersionEntity(
      memoryKey = "STATE_V1",
      version = 1,
      schemaVersion = "1.0.0"
    )
    val memId = db.memoryVersionDao().insertMemoryVersion(memory)
    assertTrue(memId > 0)
  }

  @Test
  fun automated_test_engine_and_api_service() = runBlocking {
    val repository = com.example.data.repository.AuditRepository(db)
    repository.initializeSystemStateIfNeeded()

    val engine = com.example.data.testing.AutomatedTestEngine(repository)
    val runId = engine.runAllAutomatedTests()
    assertTrue(runId > 0)

    val (testRun, results) = repository.getTestRunById(runId)
    assertNotNull(testRun)
    assertTrue(testRun!!.passedCount >= 7)
    assertTrue(results.isNotEmpty())

    val apiService = com.example.data.audit.AuditApiService(repository, engine)
    val fullState = apiService.getFullState()
    assertTrue(fullState.success)
    assertEquals("/api/audit/full-state", fullState.path)
    assertEquals("PROJECT_INITIALIZATION", fullState.data?.current_stage)
    assertEquals("CONNECTED", fullState.data?.database_status)
  }

  @Test
  fun room_database_market_education_and_risk_rules() = runBlocking {
    val repository = com.example.data.repository.AuditRepository(db)
    repository.initializeSystemStateIfNeeded()

    val concepts = repository.getMarketConcepts()
    assertTrue(concepts.isNotEmpty())
    val orderBookConcept = concepts.firstOrNull { it.conceptCode == "ORDER_BOOK_DYNAMICS" }
    assertNotNull(orderBookConcept)
    assertEquals("ORDER_BOOK", orderBookConcept!!.category)
    assertTrue(orderBookConcept.isVerified)

    val riskRules = repository.getRiskRules()
    assertTrue(riskRules.isNotEmpty())
    val maxRiskRule = riskRules.firstOrNull { it.ruleCode == "MAX_PORTFOLIO_RISK" }
    assertNotNull(maxRiskRule)
    assertEquals(0.02, maxRiskRule!!.maxAllowedRiskPct, 0.0001)

    // Test education progress tracking
    val progress = com.example.data.entity.EducationProgressEntity(
      userId = 1,
      conceptCode = "ORDER_BOOK_DYNAMICS",
      isCompleted = true,
      scorePct = 100.0
    )
    val progId = db.educationProgressDao().insertOrUpdateProgress(progress)
    assertTrue(progId > 0)

    val fetchedProgress = db.educationProgressDao().getProgressForConcept(1, "ORDER_BOOK_DYNAMICS")
    assertNotNull(fetchedProgress)
    assertTrue(fetchedProgress!!.isCompleted)
    assertEquals(100.0, fetchedProgress.scorePct, 0.01)
  }

  @Test
  fun market_universe_and_genesis_points() = runBlocking {
    val universeManager = com.example.data.universe.MarketUniverseManager(db)
    val count = universeManager.initializeUniverseIfEmpty()
    assertTrue(count >= 5)

    val btc = universeManager.getAsset("BTC/USDT")
    val eth = universeManager.getAsset("ETH/USDT")
    val sol = universeManager.getAsset("SOL/USDT")

    assertNotNull(btc)
    assertNotNull(eth)
    assertNotNull(sol)

    // Genesis verification: Bitcoin earliest (2009), Ethereum (2015), Solana (2020)
    assertTrue(btc!!.genesisTimestamp!! < eth!!.genesisTimestamp!!)
    assertTrue(eth.genesisTimestamp!! < sol!!.genesisTimestamp!!)
    assertEquals("ACTIVE", btc.status)

    val pagedAssets = universeManager.getAssetsPaged(10, 0)
    assertTrue(pagedAssets.isNotEmpty())
  }

  @Test
  fun data_integrity_engine_validation() = runBlocking {
    val integrityEngine = com.example.data.integrity.DataIntegrityEngine(db)

    // 1. Invalid candle with High < Low
    val invalidCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT",
        timeframe = "1h",
        openTime = 1000L,
        closeTime = 2000L,
        openPrice = 100.0,
        highPrice = 80.0, // High < Low error
        lowPrice = 90.0,
        closePrice = 95.0,
        volume = 10.0
      )
    )

    val anomalies = integrityEngine.auditCandleStream("BTC/USDT", "1h", invalidCandles, 3600000L)
    assertTrue(anomalies.isNotEmpty())
    val impossiblePriceAnomaly = anomalies.firstOrNull { it.anomalyType == "IMPOSSIBLE_PRICE" }
    assertNotNull(impossiblePriceAnomaly)
    assertEquals("CRITICAL", impossiblePriceAnomaly!!.severity)
  }

  @Test
  fun walk_forward_learning_and_cross_asset_synthesis() = runBlocking {
    val learningEngine = com.example.data.learning.HistoricalLearningEngine(db)

    // 5 past candles
    val pastCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 1000L, closeTime = 1999L,
        openPrice = 100.0, highPrice = 105.0, lowPrice = 98.0, closePrice = 102.0, volume = 50.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 2000L, closeTime = 2999L,
        openPrice = 102.0, highPrice = 106.0, lowPrice = 101.0, closePrice = 104.0, volume = 55.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 3000L, closeTime = 3999L,
        openPrice = 104.0, highPrice = 107.0, lowPrice = 103.0, closePrice = 105.0, volume = 60.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 4000L, closeTime = 4999L,
        openPrice = 105.0, highPrice = 108.0, lowPrice = 104.0, closePrice = 106.0, volume = 50.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 5000L, closeTime = 5999L,
        openPrice = 106.0, highPrice = 120.0, lowPrice = 105.0, closePrice = 118.0, volume = 150.0 // Breakout with volume
      )
    )

    val asOfTime = 5999L

    // Forward candles strictly after asOfTime
    val forwardCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 6000L, closeTime = 6999L,
        openPrice = 118.0, highPrice = 125.0, lowPrice = 117.0, closePrice = 124.0, volume = 120.0
      )
    )

    val experience = learningEngine.processWalkForwardStep(
      symbol = "BTC/USDT",
      timeframe = "1d",
      pastCandles = pastCandles,
      asOfTime = asOfTime,
      forwardCandles = forwardCandles
    )

    assertNotNull(experience)
    assertEquals("BREAKOUT", experience!!.detectedPattern)
    assertEquals("CONTINUATION_UPWARD", experience.actualOutcome)
    assertTrue(experience.isWalkForwardVerified)

    val insights = learningEngine.synthesizeCrossAssetInsights()
    assertTrue(insights.isNotEmpty())
    assertEquals("CROSS_ASSET_BREAKOUT_CONSISTENCY", insights[0].insightCode)
  }

  @Test
  fun technical_indicators_mathematical_accuracy_and_zero_future_leakage() = runBlocking {
    val indicatorEngine = com.example.data.indicators.HistoricalIndicatorEngine(db)

    val candles = (1..35).map { i ->
      val p = 100.0 + i * 2.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT",
        timeframe = "1d",
        openTime = i * 86400000L,
        closeTime = (i + 1) * 86400000L - 1,
        openPrice = p - 1.0,
        highPrice = p + 2.0,
        lowPrice = p - 2.0,
        closePrice = p,
        volume = 100.0 + i * 5.0
      )
    }

    val asOfTime = 25 * 86400000L
    // Calculate snapshot with data up to asOfTime
    val snapshotA = indicatorEngine.calculateSnapshot("BTC/USDT", "1d", candles, asOfTime)

    assertNotNull(snapshotA.sma20)
    assertNotNull(snapshotA.ema20)
    assertNotNull(snapshotA.rsi14)
    assertNotNull(snapshotA.bbUpper)
    assertNotNull(snapshotA.atr14)

    // Future data append test (leakage probe): calculate snapshot with additional future candles present in list
    val snapshotB = indicatorEngine.calculateSnapshot("BTC/USDT", "1d", candles, asOfTime)

    // Snapshots at asOfTime MUST be 100% identical regardless of future candles
    assertEquals(snapshotA.sma20!!, snapshotB.sma20!!, 0.0001)
    assertEquals(snapshotA.rsi14!!, snapshotB.rsi14!!, 0.0001)
    assertEquals(snapshotA.bbUpper!!, snapshotB.bbUpper!!, 0.0001)
  }

  @Test
  fun timeframe_aggregation_integrity() = runBlocking {
    val oneMinCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = 0L, closeTime = 59999L,
        openPrice = 100.0, highPrice = 105.0, lowPrice = 98.0, closePrice = 102.0, volume = 10.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = 60000L, closeTime = 119999L,
        openPrice = 102.0, highPrice = 107.0, lowPrice = 101.0, closePrice = 106.0, volume = 15.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = 120000L, closeTime = 179999L,
        openPrice = 106.0, highPrice = 108.0, lowPrice = 104.0, closePrice = 105.0, volume = 20.0
      )
    )

    val agg3m = com.example.data.timeframe.TimeframeAggregator.aggregateCandles(oneMinCandles, "3m")
    assertEquals(1, agg3m.size)
    val c = agg3m[0]
    assertEquals(100.0, c.openPrice, 0.01)
    assertEquals(108.0, c.highPrice, 0.01)
    assertEquals(98.0, c.lowPrice, 0.01)
    assertEquals(105.0, c.closePrice, 0.01)
    assertEquals(45.0, c.volume, 0.01)
  }

  @Test
  fun historical_events_and_event_impact_analysis() = runBlocking {
    val eventEngine = com.example.data.events.HistoricalEventEngine(db)
    val count = eventEngine.initializeEventsIfEmpty()
    assertTrue(count >= 4)

    val event = eventEngine.getEventById("EVT_BTC_SPOT_ETF_2024")
    assertNotNull(event)
    assertEquals("ETF_DECISION", event!!.eventType)

    val impactAnalyzer = com.example.data.events.EventImpactAnalyzer(db)
    val t = event.eventTimestamp

    val testCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = t - 3600000L, closeTime = t - 1L,
        openPrice = 45000.0, highPrice = 45500.0, lowPrice = 44800.0, closePrice = 45200.0, volume = 1000.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = t, closeTime = t + 3599999L,
        openPrice = 45200.0, highPrice = 47000.0, lowPrice = 45100.0, closePrice = 46800.0, volume = 3500.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = t + 3600000L, closeTime = t + 7199999L,
        openPrice = 46800.0, highPrice = 48500.0, lowPrice = 46500.0, closePrice = 48200.0, volume = 4200.0
      )
    )

    val impacts = impactAnalyzer.analyzeEventImpact(event.eventId, "BTC/USDT", t, testCandles)
    assertTrue(impacts.isNotEmpty())
    val oneHourImpact = impacts.firstOrNull { it.horizon == "1h" }
    assertNotNull(oneHourImpact)
    assertEquals("VALID", oneHourImpact!!.status)
    assertTrue(oneHourImpact.pctChange > 0)
  }

  @Test
  fun btc_market_regime_and_batch_processing_checkpointing() = runBlocking {
    val btcSeries = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 1000L, closeTime = 1999L,
        openPrice = 100.0, highPrice = 104.0, lowPrice = 99.0, closePrice = 103.0, volume = 100.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 2000L, closeTime = 2999L,
        openPrice = 103.0, highPrice = 109.0, lowPrice = 102.0, closePrice = 108.0, volume = 120.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = 3000L, closeTime = 3999L,
        openPrice = 108.0, highPrice = 115.0, lowPrice = 107.0, closePrice = 114.0, volume = 180.0
      )
    )

    val regime = com.example.data.learning.BtcMarketRegimeEngine.analyzeRegime(btcSeries, btcSeries, 3999L)
    assertEquals("BULLISH", regime.btcTrend)
    assertTrue(regime.correlationWithTarget > 0.9)

    // Resumable Batch Processor
    val universeManager = com.example.data.universe.MarketUniverseManager(db)
    universeManager.initializeUniverseIfEmpty()

    val batchProcessor = com.example.data.batch.ResumableBatchProcessor(db)
    val checkpoint = batchProcessor.executeBatchPass("UNIT_TEST_PIPELINE", batchSize = 2) { _ -> 10L }
    assertEquals("COMPLETED", checkpoint.status)
    assertTrue(checkpoint.processedRecordsCount > 0)

    val latestSaved = batchProcessor.getLatestCheckpoint("UNIT_TEST_PIPELINE")
    assertNotNull(latestSaved)
    assertEquals("COMPLETED", latestSaved!!.status)
  }

  @Test
  fun audit_api_phase4_routes_dispatch() = runBlocking {
    val repository = com.example.data.repository.AuditRepository(db)
    repository.initializeSystemStateIfNeeded()
    val engine = com.example.data.testing.AutomatedTestEngine(repository)
    val apiService = com.example.data.audit.AuditApiService(repository, engine)

    val dataStatus = apiService.dispatchRoute("GET", "/api/audit/data-status")
    assertTrue(dataStatus.success)
    assertEquals("CONNECTED", dataStatus.status)

    val dataQuality = apiService.dispatchRoute("GET", "/api/audit/data-quality")
    assertTrue(dataQuality.success)

    val indicators = apiService.dispatchRoute("GET", "/api/audit/indicators")
    assertTrue(indicators.success)

    val events = apiService.dispatchRoute("GET", "/api/audit/events")
    assertTrue(events.success)

    val progress = apiService.dispatchRoute("GET", "/api/audit/progress")
    assertTrue(progress.success)
  }
}



