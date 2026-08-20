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
}

