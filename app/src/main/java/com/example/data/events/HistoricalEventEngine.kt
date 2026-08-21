package com.example.data.events

import com.example.data.AppDatabase
import com.example.data.entity.HistoricalEventEntity

class HistoricalEventEngine(private val db: AppDatabase) {

    /**
     * Initializes verified benchmark historical market events if the event registry is empty.
     * All events contain exact timestamps, real historical event types, and sources.
     */
    suspend fun initializeEventsIfEmpty(): Int {
        val existing = db.historicalEventDao().getEventsList()
        if (existing.isNotEmpty()) return existing.size

        val verifiedEvents = listOf(
            HistoricalEventEntity(
                eventId = "EVT_BTC_GENESIS_2009",
                eventTimestamp = 1230940800000L, // Jan 3, 2009
                source = "BITCOIN_CORE_GENESIS",
                title = "Bitcoin Genesis Block Mined by Satoshi Nakamoto",
                eventType = "NETWORK_LAUNCH",
                affectedAssetsJson = """["BTC/USDT"]""",
                sourceUrl = "https://blockstream.info/block/000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                details = "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
            ),
            HistoricalEventEntity(
                eventId = "EVT_BTC_HALVING_2020",
                eventTimestamp = 1589222400000L, // May 11, 2020
                source = "BITCOIN_NETWORK",
                title = "Bitcoin 3rd Halving (Block 630,000)",
                eventType = "HALVING",
                affectedAssetsJson = """["BTC/USDT"]""",
                sourceUrl = "https://mempool.space/block/630000",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                details = "Block subsidy halved from 12.5 BTC to 6.25 BTC"
            ),
            HistoricalEventEntity(
                eventId = "EVT_ETH_MERGE_2022",
                eventTimestamp = 1663228800000L, // Sep 15, 2022
                source = "ETHEREUM_FOUNDATION",
                title = "Ethereum The Merge (PoW to PoS Transition)",
                eventType = "PROTOCOL_UPGRADE",
                affectedAssetsJson = """["ETH/USDT","BTC/USDT"]""",
                sourceUrl = "https://ethereum.org/en/roadmap/merge/",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                details = "Execution layer merged with Beacon Chain consensus layer at TTD 58750000000000000000000"
            ),
            HistoricalEventEntity(
                eventId = "EVT_FTX_BANKRUPTCY_2022",
                eventTimestamp = 1668124800000L, // Nov 11, 2022
                source = "US_BANKRUPTCY_COURT",
                title = "FTX and Alameda Research File for Chapter 11 Bankruptcy",
                eventType = "BANKRUPTCY",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","SOL/USDT"]""",
                sourceUrl = "https://restructuring.ra.kroll.com/FTX/",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                details = "Systemic liquidity crisis and collapse across centralized crypto credit"
            ),
            HistoricalEventEntity(
                eventId = "EVT_BTC_SPOT_ETF_2024",
                eventTimestamp = 1704931200000L, // Jan 10, 2024
                source = "US_SEC_ORDER",
                title = "US SEC Approves 11 Spot Bitcoin ETFs",
                eventType = "ETF_DECISION",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT"]""",
                sourceUrl = "https://www.sec.gov/rules/sro/nysearca/2024/34-99306.pdf",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                details = "Omnibus approval order for 19b-4 filings including BlackRock, Fidelity, Ark Invest"
            )
        )

        db.historicalEventDao().insertEvents(verifiedEvents)
        return verifiedEvents.size
    }

    suspend fun getEvents(): List<HistoricalEventEntity> = db.historicalEventDao().getEventsList()

    suspend fun getEventById(eventId: String): HistoricalEventEntity? = db.historicalEventDao().getEventById(eventId)

    suspend fun registerEvent(event: HistoricalEventEntity): Long = db.historicalEventDao().insertEvent(event)
}
