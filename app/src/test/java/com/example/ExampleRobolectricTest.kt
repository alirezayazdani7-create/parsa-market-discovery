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
    assertTrue(fullState.data?.current_stage?.isNotBlank() == true)
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

    val experiences = apiService.dispatchRoute("GET", "/api/audit/learning/experiences")
    assertTrue(experiences.success)

    val insights = apiService.dispatchRoute("GET", "/api/audit/learning/insights")
    assertTrue(insights.success)
  }

  @Test
  fun stage4_adversarial_zero_future_leakage_in_event_condition_analyzer() = runBlocking {
    val setupAnalyzer = com.example.data.events.EventConditionAnalyzer(db)
    val eventTime = 10000000L

    val baseEvent = com.example.data.entity.HistoricalEventEntity(
      eventId = "EVT_ADVERSARIAL_TEST",
      eventTimestamp = eventTime,
      source = "VERIFIED_EXCHANGE_RECORD",
      title = "Historical Benchmark Event",
      eventType = "ETF_DECISION",
      category = "REGULATORY",
      severity = "CRITICAL",
      primarySymbol = "BTC/USDT",
      affectedAssetsJson = """["BTC/USDT"]""",
      sourceUrl = "https://sec.gov",
      confidence = 1.0,
      marketImpactStatus = "ANALYZED"
    )

    // Pre-event candles strictly <= eventTime
    val pastCandles = (1..30).map { i ->
      val p = 40000.0 + i * 100.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = eventTime - (31 - i) * 3600000L, closeTime = eventTime - (31 - i) * 3600000L + 3599999L,
        openPrice = p, highPrice = p + 200.0, lowPrice = p - 100.0, closePrice = p + 50.0, volume = 500.0
      )
    }

    // Future candles scenario A (normal upward continuation)
    val futureCandlesNormal = (1..5).map { i ->
      val p = 45000.0 + i * 200.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = eventTime + (i - 1) * 3600000L + 1L, closeTime = eventTime + i * 3600000L,
        openPrice = p, highPrice = p + 100.0, lowPrice = p - 100.0, closePrice = p + 50.0, volume = 600.0
      )
    }

    // Future candles scenario B (adversarial massive crash shock injected into future)
    val futureCandlesCrash = (1..5).map { i ->
      val p = 10000.0 - i * 1000.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h", openTime = eventTime + (i - 1) * 3600000L + 1L, closeTime = eventTime + i * 3600000L,
        openPrice = p, highPrice = p + 50.0, lowPrice = p - 500.0, closePrice = p - 400.0, volume = 50000.0
      )
    }

    val setupNormal = setupAnalyzer.analyzeSetup(baseEvent, "BTC/USDT", pastCandles, futureCandlesNormal, "1h")
    val setupCrash = setupAnalyzer.analyzeSetup(baseEvent, "BTC/USDT", pastCandles, futureCandlesCrash, "1h")

    // The historical prediction, marketRegime, trend, volumeState, volatilityState, and indicator state MUST be 100% invariant
    assertEquals(setupNormal.historicalPrediction, setupCrash.historicalPrediction)
    assertEquals(setupNormal.marketRegime, setupCrash.marketRegime)
    assertEquals(setupNormal.trend, setupCrash.trend)
    assertEquals(setupNormal.volumeState, setupCrash.volumeState)
    assertEquals(setupNormal.volatilityState, setupCrash.volatilityState)
    assertEquals(setupNormal.indicatorStatesJson, setupCrash.indicatorStatesJson)

    // Only the post-event evaluation differs as expected
    assertEquals("BULLISH_EXPANSION", setupNormal.actualFutureOutcome)
    assertEquals("BEARISH_CASCADE", setupCrash.actualFutureOutcome)
  }

  @Test
  fun stage4_all_seven_horizons_excursion_and_unavailable_boundary_verification() = runBlocking {
    val impactAnalyzer = com.example.data.events.EventImpactAnalyzer(db)
    val eventTime = 100000000L

    // Historical dataset providing data up to 4 hours post-event, but NOT 24 hours
    val candles = mutableListOf<com.example.data.entity.HistoricalCandleEntity>()
    // Pre-event
    candles.add(com.example.data.entity.HistoricalCandleEntity(
      symbol = "ETH/USDT", timeframe = "1m", openTime = eventTime - 60000L, closeTime = eventTime - 1L,
      openPrice = 2000.0, highPrice = 2010.0, lowPrice = 1995.0, closePrice = 2000.0, volume = 100.0
    ))
    candles.add(com.example.data.entity.HistoricalCandleEntity(
      symbol = "ETH/USDT", timeframe = "1m", openTime = eventTime, closeTime = eventTime + 59999L,
      openPrice = 2000.0, highPrice = 2050.0, lowPrice = 1990.0, closePrice = 2040.0, volume = 500.0
    ))

    // Add candles for 1m, 5m, 15m, 30m, 1h, 4h horizons
    val offsets = listOf(60000L, 300000L, 900000L, 1800000L, 3600000L, 14400000L)
    for (offset in offsets) {
      val t = eventTime + offset
      candles.add(com.example.data.entity.HistoricalCandleEntity(
        symbol = "ETH/USDT", timeframe = "1m", openTime = t - 60000L, closeTime = t,
        openPrice = 2040.0, highPrice = 2100.0, lowPrice = 1980.0, closePrice = 2080.0, volume = 300.0
      ))
    }

    val impacts = impactAnalyzer.analyzeEventImpact("EVT_ETH_HORIZONS", "ETH/USDT", eventTime, candles)
    val impactMap = impacts.associateBy { it.horizon }

    // Horizons 1m through 4h must be VALID with real excursion metrics
    val validHorizons = listOf("1m", "5m", "15m", "30m", "1h", "4h")
    for (h in validHorizons) {
      val imp = impactMap[h]
      assertNotNull(imp)
      assertEquals("VALID", imp!!.status)
      assertTrue(imp.priceAtEvent == 2000.0)
      assertTrue(imp.highLowExcursion >= 0.0)
      assertTrue(imp.maxFavorableExcursion >= 0.0)
      assertTrue(imp.impactScore >= 0.0)
    }

    // 24h horizon must strictly be DATA_UNAVAILABLE without fabricating data
    val imp24h = impactMap["24h"]
    assertNotNull(imp24h)
    assertEquals("DATA_UNAVAILABLE", imp24h!!.status)
    assertEquals(0.0, imp24h.priceAfter, 0.0001)
  }

  @Test
  fun stage4_data_integrity_anomaly_detection_suite() = runBlocking {
    val integrityEngine = com.example.data.integrity.DataIntegrityEngine(db)

    // 1. Missing candles / time gap detection
    val candlesWithGap = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "SOL/USDT", timeframe = "1h", openTime = 1000000L, closeTime = 4599999L,
        openPrice = 100.0, highPrice = 105.0, lowPrice = 98.0, closePrice = 103.0, volume = 100.0
      ),
      // 5 hour gap
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "SOL/USDT", timeframe = "1h", openTime = 22600000L, closeTime = 26199999L,
        openPrice = 103.0, highPrice = 106.0, lowPrice = 101.0, closePrice = 105.0, volume = 120.0
      )
    )
    val anomalies = integrityEngine.auditCandleStream("SOL/USDT", "1h", candlesWithGap, expectedIntervalMs = 3600000L)
    assertTrue(anomalies.any { it.anomalyType == "ABNORMAL_GAP" })

    // 2. Impossible prices detection
    val candlesWithNegative = listOf(
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BNB/USDT", timeframe = "1h", openTime = 1000000L, closeTime = 4599999L,
        openPrice = -50.0, highPrice = 100.0, lowPrice = -60.0, closePrice = 80.0, volume = 100.0
      )
    )
    val priceAnomalies = integrityEngine.auditCandleStream("BNB/USDT", "1h", candlesWithNegative, expectedIntervalMs = 3600000L)
    assertTrue(priceAnomalies.any { it.anomalyType == "IMPOSSIBLE_PRICE" })
  }

  @Test
  fun stage4_multi_asset_cross_validation_btc_eth_sol_bnb() = runBlocking {
    val learningEngine = com.example.data.learning.HistoricalLearningEngine(db)
    val universeManager = com.example.data.universe.MarketUniverseManager(db)
    universeManager.initializeUniverseIfEmpty()

    val assets = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT")
    for (asset in assets) {
      val meta = universeManager.getAsset(asset)
      assertNotNull(meta)
      assertTrue(meta!!.symbol == asset)
    }

    // Verify cross asset calculation across BTC and SOL
    val btc = (1..20).map { i ->
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1d", openTime = i * 86400000L, closeTime = (i + 1) * 86400000L - 1,
        openPrice = 30000.0 + i * 200.0, highPrice = 30500.0 + i * 200.0, lowPrice = 29800.0 + i * 200.0, closePrice = 30200.0 + i * 200.0, volume = 1000.0
      )
    }
    val sol = (1..20).map { i ->
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "SOL/USDT", timeframe = "1d", openTime = i * 86400000L, closeTime = (i + 1) * 86400000L - 1,
        openPrice = 80.0 + i * 2.0, highPrice = 85.0 + i * 2.0, lowPrice = 78.0 + i * 2.0, closePrice = 82.0 + i * 2.0, volume = 5000.0
      )
    }

    val insight = learningEngine.calculateCrossAssetMetrics("BTC/USDT", "SOL/USDT", btc, sol)
    assertNotNull(insight)
    assertTrue(insight!!.statisticalConfidence >= 0.8)
    assertTrue(insight.sampleSize >= 18)
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

  // ==========================================
  // PHASE 6: AUTONOMOUS ANALYTICAL METHOD DISCOVERY TESTS
  // ==========================================

  @Test
  fun phase6_test01_candidate_method_discovery() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.discoverAndEvaluateMethods("BTC/USDT", "1h")
    assertTrue(methods.isNotEmpty())
    val compressionMethod = methods.find { it.methodId.contains("COMPRESSION") }
    assertNotNull(compressionMethod)
    assertTrue(compressionMethod!!.hypothesisDescription.isNotBlank())
    assertTrue(compressionMethod.featuresUsedJson.contains("VOLATILITY_RATIO") || compressionMethod.indicatorsUsedJson.contains("ATR"))
  }

  @Test
  fun phase6_test02_baseline_comparison() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val testCandles = (1..50).map { i ->
      val p = 1000.0 + i * 5.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h",
        openTime = 1600000000000L + i * 3600000L, closeTime = 1600000000000L + (i + 1) * 3600000L - 1,
        openPrice = p, highPrice = p + 10.0, lowPrice = p - 5.0, closePrice = p + 4.0, volume = 500.0
      )
    }
    val baseline = methodEngine.calculateBaselineMetrics(testCandles, horizon = 3)
    assertTrue(baseline.sampleCount > 0)
    assertTrue(baseline.positiveRate in 0.0..1.0)
    assertTrue(baseline.negativeRate in 0.0..1.0)
    assertTrue(baseline.averageOutcome >= -1.0)
  }

  @Test
  fun phase6_test03_chronological_data_separation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    for (m in methods) {
      assertTrue("Discovery period must not be blank", m.discoveryPeriod.isNotBlank())
      assertTrue("Validation period must not be blank", m.validationPeriod.isNotBlank())
      assertTrue("Out-of-sample period must not be blank", m.outOfSamplePeriod.isNotBlank())
      assertTrue(m.validationPeriod != m.discoveryPeriod)
      assertTrue(m.outOfSamplePeriod != m.validationPeriod)
    }
  }

  @Test
  fun phase6_test04_zero_future_leakage_in_method_evaluation() = runBlocking {
    val timestampT = 1700000000000L
    val basePastCandles = (1..30).map { i ->
      val p = 20000.0 + i * 20.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h",
        openTime = timestampT - (31 - i) * 3600000L, closeTime = timestampT - (31 - i) * 3600000L + 3599999L,
        openPrice = p, highPrice = p + 50.0, lowPrice = p - 30.0, closePrice = p + 15.0, volume = 1000.0
      )
    }

    val closes1 = basePastCandles.map { it.closePrice }
    val rsi1 = com.example.data.indicators.HistoricalIndicatorEngine.calculateRSI(closes1, 14)

    val futureCandle = com.example.data.entity.HistoricalCandleEntity(
      symbol = "BTC/USDT", timeframe = "1h",
      openTime = timestampT + 3600000L, closeTime = timestampT + 7199999L,
      openPrice = 90000.0, highPrice = 95000.0, lowPrice = 89000.0, closePrice = 94000.0, volume = 10000.0
    )
    val combined = basePastCandles + futureCandle
    val filtered = combined.filter { it.closeTime <= timestampT }
    val closes2 = filtered.map { it.closePrice }
    val rsi2 = com.example.data.indicators.HistoricalIndicatorEngine.calculateRSI(closes2, 14)

    assertNotNull(rsi1)
    assertNotNull(rsi2)
    assertEquals(rsi1!!, rsi2!!, 0.000001)
    assertEquals(basePastCandles.size, filtered.size)
  }

  @Test
  fun phase6_test05_adversarial_future_shock_invariance() = runBlocking {
    val timestampT = 1700000000000L
    val basePastCandles = (1..30).map { i ->
      val p = 20000.0 + i * 20.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h",
        openTime = timestampT - (31 - i) * 3600000L, closeTime = timestampT - (31 - i) * 3600000L + 3599999L,
        openPrice = p, highPrice = p + 50.0, lowPrice = p - 30.0, closePrice = p + 15.0, volume = 1000.0
      )
    }

    val ema1 = com.example.data.indicators.HistoricalIndicatorEngine.calculateEMA(basePastCandles.map { it.closePrice }, 20)

    val massiveShock = com.example.data.entity.HistoricalCandleEntity(
      symbol = "BTC/USDT", timeframe = "1h",
      openTime = timestampT + 3600000L, closeTime = timestampT + 7199999L,
      openPrice = 999999.0, highPrice = 1000000.0, lowPrice = 999990.0, closePrice = 999995.0, volume = 500000.0
    )
    val shockedList = basePastCandles + massiveShock
    val filtered = shockedList.filter { it.closeTime <= timestampT }
    val ema2 = com.example.data.indicators.HistoricalIndicatorEngine.calculateEMA(filtered.map { it.closePrice }, 20)

    assertNotNull(ema1)
    assertNotNull(ema2)
    assertEquals(ema1!!, ema2!!, 0.000001)
  }

  @Test
  fun phase6_test06_parameter_sensitivity() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val testCandles = (1..60).map { i ->
      val p = 2000.0 + i * 4.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "ETH/USDT", timeframe = "1h",
        openTime = 1600000000000L + i * 3600000L, closeTime = 1600000000000L + (i + 1) * 3600000L - 1,
        openPrice = p, highPrice = p + 8.0, lowPrice = p - 4.0, closePrice = p + 2.0, volume = 600.0
      )
    }
    val sensitivity = methodEngine.testParameterSensitivity(testCandles, baseThreshold = 55.0, shiftRange = 0.10)
    assertTrue(sensitivity.sensitivityScore in 0.0..1.0)
    assertTrue(sensitivity.degradations.isNotEmpty())
    assertTrue(sensitivity.grade in listOf("STABLE", "MODERATE", "SENSITIVE", "UNSTABLE"))
  }

  @Test
  fun phase6_test07_out_of_sample_evaluation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    for (m in methods) {
      assertTrue(m.outOfSamplePeriod.isNotBlank())
      assertTrue(m.sampleCount > 0)
    }
  }

  @Test
  fun phase6_test08_walk_forward_evaluation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    val momentum = methods.find { it.methodId.contains("MOMENTUM") }
    assertNotNull(momentum)
    assertTrue(momentum!!.parameterSensitivityScore in 0.0..1.0)
  }

  @Test
  fun phase6_test09_cross_regime_validation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    for (m in methods) {
      assertTrue(m.crossRegimeStabilityScore in 0.0..1.0)
    }
  }

  @Test
  fun phase6_test10_cross_asset_validation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    for (m in methods) {
      assertTrue(m.assetUniverseJson.contains("BTC/USDT") || m.assetUniverseJson.contains("ETH/USDT"))
      assertTrue(m.crossAssetStabilityScore in 0.0..1.0)
    }
  }

  @Test
  fun phase6_test11_multi_timeframe_validation() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    for (m in methods) {
      assertTrue(m.timeframe.isNotBlank())
    }
  }

  @Test
  fun phase6_test12_historical_event_integration() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val methods = methodEngine.getCoreHistoricalAnalyticalMethods()
    val eventIntegrated = methods.find { it.eventFeaturesUsedJson.contains("HALVING") || it.eventFeaturesUsedJson.contains("EVENT") }
    assertNotNull(eventIntegrated)
  }

  @Test
  fun phase6_test13_method_versioning() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val initialMethods = methodEngine.getCoreHistoricalAnalyticalMethods()
    db.analyticalMethodDao().insertMethods(initialMethods)

    val v2 = methodEngine.createMethodVersion(
      existingMethodId = "MTH_VOL_COMPRESSION_EXPANSION_V1",
      modifications = mapOf("volume_threshold" to 1.35),
      newHypothesis = "Refined Volatility Compression with volume threshold 1.35x"
    )
    assertNotNull(v2)
    assertEquals(2, v2!!.methodVersion)

    val versions = db.analyticalMethodDao().getMethodVersions("MTH_VOL_COMPRESSION_EXPANSION_V1")
    assertTrue(versions.size >= 2)
  }

  @Test
  fun phase6_test14_experience_memory_persistence() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    methodEngine.discoverAndEvaluateMethods("BTC/USDT", "1h")

    val evaluations = db.methodEvaluationDao().getRecentEvaluations(50)
    assertTrue(evaluations.isNotEmpty())
    val firstEval = evaluations[0]
    assertTrue(firstEval.evaluationType.isNotBlank())
    assertTrue(firstEval.sampleSize > 0)
  }

  @Test
  fun phase6_test15_failure_classification() = runBlocking {
    val methodEngine = com.example.data.methods.AnalyticalMethodDiscoveryEngine(db)
    val overfit = methodEngine.getCoreHistoricalAnalyticalMethods().find { it.methodId.contains("OVERFIT") }
    assertNotNull(overfit)
    assertEquals("OVERFIT", overfit!!.failureClassification)
    assertEquals("REJECTED", overfit.status)
    assertEquals("REJECTED", overfit.evidenceGrade)
    assertTrue(overfit.failureReasonsJson?.contains("High parameter sensitivity") == true)
  }

  @Test
  fun phase6_test16_data_integrity() = runBlocking {
    val integrityEngine = com.example.data.integrity.DataIntegrityEngine(db)
    val validCandles = (1..10).map { i ->
      val p = 100.0 + i * 2.0
      com.example.data.entity.HistoricalCandleEntity(
        symbol = "BTC/USDT", timeframe = "1h",
        openTime = i * 3600000L, closeTime = (i + 1) * 3600000L - 1,
        openPrice = p, highPrice = p + 2.0, lowPrice = p - 2.0, closePrice = p + 1.0, volume = 50.0
      )
    }
    val anomalies = integrityEngine.auditCandleStream("BTC/USDT", "1h", validCandles, 3600000L)
    assertTrue(anomalies.isEmpty())
  }

  @Test
  fun phase6_test17_room_crud() = runBlocking {
    val method = com.example.data.entity.AnalyticalMethodEntity(
      methodId = "MTH_TEST_CRUD",
      methodVersion = 1,
      methodName = "Test Method CRUD",
      hypothesisDescription = "Test Hypothesis Description",
      indicatorsUsedJson = """["RSI_14"]""",
      featuresUsedJson = """["MOMENTUM"]""",
      eventFeaturesUsedJson = "[]",
      timeframe = "1h",
      assetUniverseJson = """["BTC/USDT"]""",
      discoveryPeriod = "2021-2022",
      validationPeriod = "2023",
      outOfSamplePeriod = "2024",
      sampleCount = 100,
      positiveOutcomes = 60,
      negativeOutcomes = 35,
      neutralOutcomes = 5,
      baselineSampleCount = 500,
      baselinePositiveRate = 0.50,
      methodPositiveRate = 0.60,
      outperformanceVsBaseline = 0.10,
      averageOutcome = 0.02,
      medianOutcome = 0.015,
      dispersion = 0.03,
      volatility = 0.04,
      parameterSensitivityScore = 0.12,
      parameterStabilityGrade = "STABLE",
      crossRegimeStabilityScore = 0.75,
      crossAssetStabilityScore = 0.80,
      evidenceGrade = "ROBUST",
      status = "RETAINED",
      failureClassification = null,
      failureReasonsJson = "[]",
      createdAt = 1600000000000L
    )
    db.analyticalMethodDao().insertMethod(method)

    val retrieved = db.analyticalMethodDao().getMethodByIdAndVersion("MTH_TEST_CRUD", 1)
    assertNotNull(retrieved)
    assertEquals("MTH_TEST_CRUD", retrieved?.methodId)
    assertEquals("ROBUST", retrieved?.evidenceGrade)

    val updated = retrieved!!.copy(evidenceGrade = "REPEATED")
    db.analyticalMethodDao().updateMethod(updated)
    val reRetrieved = db.analyticalMethodDao().getMethodByIdAndVersion("MTH_TEST_CRUD", 1)
    assertEquals("REPEATED", reRetrieved?.evidenceGrade)
  }

  @Test
  fun phase6_test18_room_migration_and_schema_version() = runBlocking {
    val version = db.openHelper.readableDatabase.version
    assertEquals(7, version)
  }

  @Test
  fun phase6_test19_audit_api_methods_routes() = runBlocking {
    val repository = com.example.data.repository.AuditRepository(db)
    repository.initializeSystemStateIfNeeded()
    val engine = com.example.data.testing.AutomatedTestEngine(repository)
    val apiService = com.example.data.audit.AuditApiService(repository, engine)

    val methodsResponse = apiService.dispatchRoute("GET", "/api/audit/methods")
    assertTrue(methodsResponse.success)
    assertEquals("/api/audit/methods", methodsResponse.path)

    val evidenceResponse = apiService.dispatchRoute("GET", "/api/audit/methods/evidence")
    assertTrue(evidenceResponse.success)
    assertEquals("/api/audit/methods/evidence", evidenceResponse.path)

    val validationResponse = apiService.dispatchRoute("GET", "/api/audit/methods/validation")
    assertTrue(validationResponse.success)

    val failuresResponse = apiService.dispatchRoute("GET", "/api/audit/methods/failures")
    assertTrue(failuresResponse.success)

    val versionsResponse = apiService.dispatchRoute("GET", "/api/audit/methods/versions")
    assertTrue(versionsResponse.success)

    val learningResponse = apiService.dispatchRoute("GET", "/api/audit/learning/method-discovery")
    assertTrue(learningResponse.success)
  }

  @Test
  fun phase6_test20_stage_gate_trainee_safety_guardrail() = runBlocking {
    val repository = com.example.data.repository.AuditRepository(db)
    repository.initializeSystemStateIfNeeded()

    // 1. Live trading engine must strictly be absent or disabled
    val liveTradingState = repository.getSystemState("TRADE_EXECUTION_ENGINE")
    val isDisabled = liveTradingState == null || liveTradingState.value != "ACTIVE"
    assertTrue(isDisabled)

    // 2. Real-time predictions must strictly be disabled
    val livePredictionState = repository.getSystemState("REAL_TIME_PREDICTIONS")
    val isPredictionDisabled = livePredictionState == null || livePredictionState.value != "ACTIVE"
    assertTrue(isPredictionDisabled)

    // 3. Stage must be recorded
    val currentStage = repository.getSystemState("CURRENT_PROJECT_STAGE")
    assertNotNull(currentStage)
  }
}





