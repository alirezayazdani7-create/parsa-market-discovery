package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AuditLogDao
import com.example.data.dao.CrossAssetInsightDao
import com.example.data.dao.DataIntegrityAnomalyDao
import com.example.data.dao.EducationProgressDao
import com.example.data.dao.ExperienceMemoryDao
import com.example.data.dao.ExperimentDao
import com.example.data.dao.HistoricalCandleDao
import com.example.data.dao.MarketAssetDao
import com.example.data.dao.MarketConceptDao
import com.example.data.dao.MemoryVersionDao
import com.example.data.dao.ModelVersionDao
import com.example.data.dao.RiskRuleDao
import com.example.data.dao.SystemStateDao
import com.example.data.dao.TestResultDao
import com.example.data.dao.TestRunDao
import com.example.data.dao.UserDao
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.CrossAssetInsightEntity
import com.example.data.entity.DataIntegrityAnomalyEntity
import com.example.data.entity.EducationProgressEntity
import com.example.data.entity.ExperienceMemoryEntity
import com.example.data.entity.ExperimentEntity
import com.example.data.entity.HistoricalCandleEntity
import com.example.data.entity.MarketAssetEntity
import com.example.data.entity.MarketConceptEntity
import com.example.data.entity.MemoryVersionEntity
import com.example.data.entity.ModelVersionEntity
import com.example.data.entity.RiskRuleEntity
import com.example.data.entity.SystemStateEntity
import com.example.data.entity.TestResultEntity
import com.example.data.entity.TestRunEntity
import com.example.data.entity.UserEntity

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
        DataIntegrityAnomalyEntity::class
    ],
    version = 3,
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

