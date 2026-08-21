package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.*
import com.example.data.entity.*

@Database(
    entities = [
        UserEntity::class,
        SystemStateEntity::class,
        ExperimentEntity::class,
        TestRunEntity::class,
        TestResultEntity::class,
        AuditLogEntity::class,
        ModelVersionEntity::class,
        MemoryVersionEntity::class,
        MarketConceptEntity::class,
        RiskRuleEntity::class,
        EducationProgressEntity::class,
        MarketAssetEntity::class,
        HistoricalCandleEntity::class,
        ExperienceMemoryEntity::class,
        CrossAssetInsightEntity::class,
        DataIntegrityAnomalyEntity::class,
        HistoricalEventEntity::class,
        EventImpactEntity::class,
        HistoricalIndicatorSnapshotEntity::class,
        BatchProcessingCheckpointEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun systemStateDao(): SystemStateDao
    abstract fun experimentDao(): ExperimentDao
    abstract fun testRunDao(): TestRunDao
    abstract fun testResultDao(): TestResultDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun modelVersionDao(): ModelVersionDao
    abstract fun memoryVersionDao(): MemoryVersionDao
    abstract fun marketConceptDao(): MarketConceptDao
    abstract fun riskRuleDao(): RiskRuleDao
    abstract fun educationProgressDao(): EducationProgressDao
    abstract fun marketAssetDao(): MarketAssetDao
    abstract fun historicalCandleDao(): HistoricalCandleDao
    abstract fun experienceMemoryDao(): ExperienceMemoryDao
    abstract fun crossAssetInsightDao(): CrossAssetInsightDao
    abstract fun dataIntegrityAnomalyDao(): DataIntegrityAnomalyDao
    abstract fun historicalEventDao(): HistoricalEventDao
    abstract fun eventImpactDao(): EventImpactDao
    abstract fun historicalIndicatorDao(): HistoricalIndicatorDao
    abstract fun batchProcessingCheckpointDao(): BatchProcessingCheckpointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parsa_core_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

