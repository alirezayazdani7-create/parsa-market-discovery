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

    val setups = apiService.dispatchRoute("GET", "/api/audit/setups")
    assertTrue(setups.success)
  }

  @Test
  fun stage4_historical_event_integrity_verification() = runBlocking {
    val eventEngine = com.example.data.events.HistoricalEventEngine(db)
    val count = eventEngine.initializeEventsIfEmpty()
    assertTrue(count >= 7)

    val events = eventEngine.getEvents()
    for (event in events) {
      assertTrue(event.eventId.isNotBlank())
      assertTrue(event.eventTimestamp > 0)
      assertTrue(event.source.isNotBlank())
      assertTrue(event.sourceUrl.startsWith("http"))
      assertEquals(1.0, event.confidence, 0.001)
      assertTrue(event.primarySymbol.isNotBlank())
      assertTrue(event.affectedAssetsJson.contains("BTC/USDT") || event.affectedAssetsJson.contains("ETH/USDT"))
    }

    val regulatoryEvents = eventEngine.getEventsByCategory("REGULATORY")
    assertTrue(regulatoryEvents.isNotEmpty())
    val etfEvent = regulatoryEvents.first { it.eventId == "EVT_BTC_SPOT_ETF_2024" }
    assertEquals("ETF_DECISION", etfEvent.eventType)
    assertEquals("CRITICAL", etfEvent.severity)
  }

  @Test
  fun stage4_zero_future_leakage_in_indicators_and_snapshots() = runBlocking {
    val indicatorEngine = com.example.data.indicators.HistoricalIndicatorEngine(db)

    val historicalCandles = (1..60).map { i ->
      val p = 100.0 + (i % 10) * 3.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT",
        timeframe = "1h",
        openTime = i * 3600000L,
        closeTime = (i + 1) * 3600000L - 1,
        openPrice = p - 1.0,
        highPrice = p + 4.0,
        lowPrice = p - 3.0,
        closePrice = p,
        volume = 50.0 + i * 2.0
      )
    }

    val asOfTime = 30 * 3600000L
    // Subset of candles <= asOfTime
    val pastOnlyCandles = historicalCandles.filter { it.openTime <= asOfTime }

    val snapshotFromPastOnly = indicatorEngine.calculateSnapshot("BTC/USDT", "1h", pastOnlyCandles, asOfTime)
    val snapshotFromAllCandles = indicatorEngine.calculateSnapshot("BTC/USDT", "1h", historicalCandles, asOfTime)

    // Snapshot calculated with future candles present MUST be bit-identical to snapshot calculated with ONLY past candles
    assertEquals(snapshotFromPastOnly.sma20!!, snapshotFromAllCandles.sma20!!, 0.00001)
    assertEquals(snapshotFromPastOnly.ema20!!, snapshotFromAllCandles.ema20!!, 0.00001)
    assertEquals(snapshotFromPastOnly.rsi14!!, snapshotFromAllCandles.rsi14!!, 0.00001)
    assertEquals(snapshotFromPastOnly.bbUpper!!, snapshotFromAllCandles.bbUpper!!, 0.00001)
    assertEquals(snapshotFromPastOnly.atr14!!, snapshotFromAllCandles.atr14!!, 0.00001)
    assertEquals(snapshotFromPastOnly.vwap!!, snapshotFromAllCandles.vwap!!, 0.00001)
    assertEquals(snapshotFromPastOnly.obv!!, snapshotFromAllCandles.obv!!, 0.00001)
  }

  @Test
  fun stage4_zero_future_leakage_in_event_impact_and_excursion_metrics() = runBlocking {
    val impactAnalyzer = com.example.data.events.EventImpactAnalyzer(db)
    val eventTime = 5000000L

    val pastAndEventCandles = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = eventTime - 60000L, closeTime = eventTime - 1L,
        openPrice = 50000.0, highPrice = 50100.0, lowPrice = 49950.0, closePrice = 50050.0, volume = 10.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = eventTime, closeTime = eventTime + 59999L,
        openPrice = 50050.0, highPrice = 50500.0, lowPrice = 50000.0, closePrice = 50400.0, volume = 50.0
      ),
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1m", openTime = eventTime + 60000L, closeTime = eventTime + 119999L,
        openPrice = 50400.0, highPrice = 51000.0, lowPrice = 50350.0, closePrice = 50900.0, volume = 80.0
      )
    )

    val impacts = impactAnalyzer.analyzeEventImpact("EVT_TEST_LEAKAGE", "BTC/USDT", eventTime, pastAndEventCandles)
    assertTrue(impacts.isNotEmpty())

    val impact1m = impacts.first { it.horizon == "1m" }
    assertEquals("VALID", impact1m.status)
    assertEquals("UP", impact1m.direction)
    assertTrue(impact1m.maxFavorableExcursion > 0)
    assertTrue(impact1m.impactScore > 0)

    // 24h horizon must be gracefully marked DATA_UNAVAILABLE since no 24h future candles exist
    val impact24h = impacts.first { it.horizon == "24h" }
    assertEquals("DATA_UNAVAILABLE", impact24h.status)
  }

  @Test
  fun stage4_multi_timeframe_alignment_and_closed_candle_boundary() = runBlocking {
    val source1m = (0 until 120).map { i ->
      val t = i * 60000L
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT",
        timeframe = "1m",
        openTime = t,
        closeTime = t + 59999L,
        openPrice = 100.0 + i * 0.5,
        highPrice = 101.0 + i * 0.5,
        lowPrice = 99.5 + i * 0.5,
        closePrice = 100.5 + i * 0.5,
        volume = 10.0
      )
    }

    // As of 75 minutes (t = 4500000L)
    val asOfTime = 4500000L
    val multiTf = com.example.data.timeframe.TimeframeAggregator.alignClosedMultiTimeframe(source1m, asOfTime = asOfTime)

    val candle1h = multiTf["1h"]
    assertNotNull(candle1h)
    // The 1h candle MUST close at 3599999L <= 4500000L
    assertTrue(candle1h!!.closeTime <= asOfTime)
    assertEquals(0L, candle1h.openTime)
    assertEquals(3599999L, candle1h.closeTime)

    // The 4h candle is NOT closed at 75 minutes (needs 240 minutes) -> must be null
    val candle4h = multiTf["4h"]
    assertTrue(candle4h == null)
  }

  @Test
  fun stage4_walk_forward_learning_monotonicity_and_simulation() = runBlocking {
    val learningEngine = com.example.data.learning.HistoricalLearningEngine(db)

    val candles = (0 until 40).map { i ->
      val t = i * 86400000L
      val p = 100.0 + i * 1.5
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT",
        timeframe = "1d",
        openTime = t,
        closeTime = t + 86399999L,
        openPrice = p - 0.5,
        highPrice = p + 2.0,
        lowPrice = p - 1.0,
        closePrice = p,
        volume = 100.0 + i * 5.0
      )
    }

    val experiences = learningEngine.runWalkForwardSimulation("BTC/USDT", "1d", candles, windowSize = 10, forwardHorizon = 5)
    assertTrue(experiences.isNotEmpty())

    // Verify strict chronological monotonicity across generated experience memories
    var previousTime = -1L
    for (exp in experiences) {
      assertTrue(exp.timestamp > previousTime)
      assertTrue(exp.isWalkForwardVerified)
      assertTrue(exp.prediction.isNotBlank())
      assertTrue(exp.actualOutcome != null)
      previousTime = exp.timestamp
    }

    // Audit query verifications
    val mistakes = learningEngine.queryMistakes("BTC/USDT")
    assertNotNull(mistakes)
    val lessons = learningEngine.queryLessonsLearned()
    assertTrue(lessons.isNotEmpty())
  }

  @Test
  fun stage4_cross_asset_statistical_intelligence() = runBlocking {
    val learningEngine = com.example.data.learning.HistoricalLearningEngine(db)

    val btcCandles = (1..30).map { i ->
      val t = i * 86400000L
      val p = 20000.0 + i * 500.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = t, closeTime = t + 86399999L,
        openPrice = p, highPrice = p + 300.0, lowPrice = p - 200.0, closePrice = p + 200.0, volume = 5000.0
      )
    }

    val ethCandles = (1..30).map { i ->
      val t = i * 86400000L
      val p = 1500.0 + i * 40.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "ETH/USDT", timeframe = "1d", openTime = t, closeTime = t + 86399999L,
        openPrice = p, highPrice = p + 25.0, lowPrice = p - 15.0, closePrice = p + 15.0, volume = 8000.0
      )
    }

    val crossInsight = learningEngine.calculateCrossAssetMetrics("BTC/USDT", "ETH/USDT", btcCandles, ethCandles)
    assertNotNull(crossInsight)
    assertEquals("CROSS_ASSET_RETURN_CORRELATION", crossInsight!!.patternOrConcept)
    assertTrue(crossInsight.sampleSize >= 28)
    assertTrue(crossInsight.statisticalConfidence > 0.8)
    assertTrue(crossInsight.correlatedAssetsJson.contains("correlation"))
  }

  @Test
  fun stage4_data_integrity_audit_and_event_ingestion_validation() = runBlocking {
    val integrityEngine = com.example.data.integrity.DataIntegrityEngine(db)

    // Valid event
    val validEvent = com.example.data.entity.HistoricalEventEntity(
      eventId = "EVT_VALID_001",
      eventTimestamp = 1600000000000L,
      source = "REUTERS_FINANCE",
      title = "Major Verified Market Event",
      eventType = "REGULATORY",
      category = "REGULATORY",
      severity = "HIGH",
      primarySymbol = "BTC/USDT",
      affectedAssetsJson = """["BTC/USDT"]""",
      sourceUrl = "https://reuters.com",
      confidence = 1.0,
      marketImpactStatus = "ANALYZED"
    )

    val validAnomaly = integrityEngine.auditEventIngestion(validEvent)
    assertTrue(validAnomaly == null)

    // Invalid event (blank ID or 0 timestamp)
    val invalidEvent = validEvent.copy(eventId = "", eventTimestamp = 0L)
    val anomaly = integrityEngine.auditEventIngestion(invalidEvent)
    assertNotNull(anomaly)
    assertEquals("INVALID_EVENT_METADATA", anomaly!!.anomalyType)
    assertEquals("HIGH", anomaly.severity)
  }
}




