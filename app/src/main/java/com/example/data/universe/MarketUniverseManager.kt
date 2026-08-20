package com.example.data.universe

import com.example.data.AppDatabase
import com.example.data.entity.MarketAssetEntity

class MarketUniverseManager(private val db: AppDatabase) {

    suspend fun initializeUniverseIfEmpty(): Int {
        val count = db.marketAssetDao().getAssetsCount()
        if (count > 0) return count

        // Initialize core reference assets with actual Genesis points (no fake backfills)
        val initialUniverse = listOf(
            MarketAssetEntity(
                symbol = "BTC/USDT",
                name = "Bitcoin",
                marketType = "SPOT",
                exchange = "PRIMARY_AGGREGATOR",
                marketCapRank = 1,
                genesisTimestamp = 1230940800000L, // Jan 3, 2009 (Genesis block timestamp)
                firstSeenAt = 1279324800000L,     // Jul 17, 2010 (Earliest market trading archive)
                supportedTimeframes = "1m,5m,15m,30m,1h,4h,1d,1w",
                status = "ACTIVE",
                sourceMetadataJson = """{"layer":1,"consensus":"POW","maxSupply":21000000}"""
            ),
            MarketAssetEntity(
                symbol = "ETH/USDT",
                name = "Ethereum",
                marketType = "SPOT",
                exchange = "PRIMARY_AGGREGATOR",
                marketCapRank = 2,
                genesisTimestamp = 1438214400000L, // Jul 30, 2015 (Ethereum Genesis)
                firstSeenAt = 1438905600000L,     // Aug 7, 2015 (Earliest market trading)
                supportedTimeframes = "1m,5m,15m,30m,1h,4h,1d,1w",
                status = "ACTIVE",
                sourceMetadataJson = """{"layer":1,"consensus":"POS"}"""
            ),
            MarketAssetEntity(
                symbol = "SOL/USDT",
                name = "Solana",
                marketType = "SPOT",
                exchange = "PRIMARY_AGGREGATOR",
                marketCapRank = 3,
                genesisTimestamp = 1584316800000L, // Mar 16, 2020 (Solana Mainnet Genesis)
                firstSeenAt = 1586563200000L,     // Apr 11, 2020 (Trading debut)
                supportedTimeframes = "1m,5m,15m,30m,1h,4h,1d,1w",
                status = "ACTIVE",
                sourceMetadataJson = """{"layer":1,"consensus":"POH_POS"}"""
            ),
            MarketAssetEntity(
                symbol = "BNB/USDT",
                name = "BNB",
                marketType = "SPOT",
                exchange = "PRIMARY_AGGREGATOR",
                marketCapRank = 4,
                genesisTimestamp = 1499472000000L, // Jul 8, 2017 (BNB Launch)
                firstSeenAt = 1500940800000L,     // Jul 25, 2017
                supportedTimeframes = "1m,5m,15m,30m,1h,4h,1d,1w",
                status = "ACTIVE",
                sourceMetadataJson = """{"layer":1,"ecosystem":"BSC"}"""
            ),
            MarketAssetEntity(
                symbol = "XRP/USDT",
                name = "XRP",
                marketType = "SPOT",
                exchange = "PRIMARY_AGGREGATOR",
                marketCapRank = 5,
                genesisTimestamp = 1357084800000L, // Jan 2, 2013
                firstSeenAt = 1375660800000L,     // Aug 5, 2013
                supportedTimeframes = "1m,5m,15m,30m,1h,4h,1d,1w",
                status = "ACTIVE",
                sourceMetadataJson = """{"network":"RippleNet"}"""
            )
        )
        db.marketAssetDao().insertAssets(initialUniverse)
        return initialUniverse.size
    }

    suspend fun getUniverseCount(): Int = db.marketAssetDao().getAssetsCount()

    suspend fun getAssetsPaged(limit: Int, offset: Int): List<MarketAssetEntity> =
        db.marketAssetDao().getAssetsPaged(limit, offset)

    suspend fun registerAsset(asset: MarketAssetEntity): Long =
        db.marketAssetDao().insertAsset(asset)

    suspend fun getAsset(symbol: String): MarketAssetEntity? =
        db.marketAssetDao().getAssetBySymbol(symbol)
}
