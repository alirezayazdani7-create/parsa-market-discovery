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
}


