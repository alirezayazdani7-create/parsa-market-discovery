package com.example.data.batch

import com.example.data.AppDatabase
import com.example.data.entity.BatchProcessingCheckpointEntity
import com.example.data.entity.MarketAssetEntity

class ResumableBatchProcessor(private val db: AppDatabase) {

    /**
     * Executes a batch learning pass across registered universe assets with persistent checkpointing.
     * If interrupted, execution resumes from the exact index and symbol of the last successful checkpoint.
     */
    suspend fun executeBatchPass(
        pipelineName: String = "HISTORICAL_RESEARCH_PIPELINE",
        batchSize: Int = 50,
        assetProcessor: suspend (asset: MarketAssetEntity) -> Long
    ): BatchProcessingCheckpointEntity {
        val totalCount = db.marketAssetDao().getAssetsCount()
        val latestCheckpoint = db.batchProcessingCheckpointDao().getLatestCheckpoint(pipelineName)

        val startIndex = if (latestCheckpoint != null && latestCheckpoint.status == "IN_PROGRESS") {
            latestCheckpoint.lastProcessedAssetIndex + 1
        } else {
            0
        }

        var currentIndex = startIndex
        var totalRecordsProcessed = latestCheckpoint?.processedRecordsCount ?: 0L
        var lastSymbol = latestCheckpoint?.lastProcessedSymbol ?: ""

        try {
            while (currentIndex < totalCount) {
                val batch = db.marketAssetDao().getAssetsPaged(limit = batchSize, offset = currentIndex)
                if (batch.isEmpty()) break

                for (asset in batch) {
                    val processedCount = assetProcessor(asset)
                    totalRecordsProcessed += processedCount
                    lastSymbol = asset.symbol

                    currentIndex++

                    // Save checkpoint on every batch boundary or on demand
                    if (currentIndex % batchSize == 0 || currentIndex == totalCount) {
                        val checkpoint = BatchProcessingCheckpointEntity(
                            pipelineName = pipelineName,
                            lastProcessedAssetIndex = currentIndex - 1,
                            lastProcessedSymbol = lastSymbol,
                            lastProcessedTimestamp = System.currentTimeMillis(),
                            totalAssetsCount = totalCount,
                            processedRecordsCount = totalRecordsProcessed,
                            batchSize = batchSize,
                            status = if (currentIndex >= totalCount) "COMPLETED" else "IN_PROGRESS"
                        )
                        db.batchProcessingCheckpointDao().saveCheckpoint(checkpoint)
                    }
                }
            }

            val finalCheckpoint = BatchProcessingCheckpointEntity(
                pipelineName = pipelineName,
                lastProcessedAssetIndex = if (totalCount > 0) totalCount - 1 else 0,
                lastProcessedSymbol = lastSymbol,
                lastProcessedTimestamp = System.currentTimeMillis(),
                totalAssetsCount = totalCount,
                processedRecordsCount = totalRecordsProcessed,
                batchSize = batchSize,
                status = "COMPLETED"
            )
            db.batchProcessingCheckpointDao().saveCheckpoint(finalCheckpoint)
            return finalCheckpoint
        } catch (e: Exception) {
            val failedCheckpoint = BatchProcessingCheckpointEntity(
                pipelineName = pipelineName,
                lastProcessedAssetIndex = currentIndex,
                lastProcessedSymbol = lastSymbol,
                lastProcessedTimestamp = System.currentTimeMillis(),
                totalAssetsCount = totalCount,
                processedRecordsCount = totalRecordsProcessed,
                batchSize = batchSize,
                status = "FAILED",
                errorMessage = e.message
            )
            db.batchProcessingCheckpointDao().saveCheckpoint(failedCheckpoint)
            return failedCheckpoint
        }
    }

    suspend fun getLatestCheckpoint(pipelineName: String = "HISTORICAL_RESEARCH_PIPELINE"): BatchProcessingCheckpointEntity? =
        db.batchProcessingCheckpointDao().getLatestCheckpoint(pipelineName)
}
