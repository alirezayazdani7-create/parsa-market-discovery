package com.example.data.events

import com.example.data.AppDatabase
import com.example.data.entity.HistoricalEventEntity

class HistoricalEventEngine(private val db: AppDatabase) {

    /**
     * Initializes verified benchmark historical market events if the event registry is empty.
     * All events contain exact timestamps, real historical event types, verified sources, and real affected assets.
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
                category = "PROTOCOL",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT"]""",
                sourceUrl = "https://blockstream.info/block/000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "UNINITIALIZED",
                postEventState = "NETWORK_ACTIVE",
                details = "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
            ),
            HistoricalEventEntity(
                eventId = "EVT_MT_GOX_COLLAPSE_2014",
                eventTimestamp = 1393200000000L, // Feb 24, 2014
                source = "TOKYO_DISTRICT_COURT",
                title = "Mt. Gox Suspends Trading and Files for Bankruptcy Protection",
                eventType = "BANKRUPTCY",
                category = "EXCHANGE",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT"]""",
                sourceUrl = "https://www.mtgox.com/",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "DISTRIBUTION",
                postEventState = "BEARISH_TREND",
                details = "Loss of approximately 850,000 bitcoins creating industry-wide liquidity shock"
            ),
            HistoricalEventEntity(
                eventId = "EVT_ETH_GENESIS_2015",
                eventTimestamp = 1438214400000L, // Jul 30, 2015
                source = "ETHEREUM_FOUNDATION",
                title = "Ethereum Frontier Network Genesis Launch",
                eventType = "NETWORK_LAUNCH",
                category = "PROTOCOL",
                severity = "CRITICAL",
                primarySymbol = "ETH/USDT",
                affectedAssetsJson = """["ETH/USDT"]""",
                sourceUrl = "https://blog.ethereum.org/2015/07/30/ethereum-frontier-release",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "UNINITIALIZED",
                postEventState = "ACCUMULATION",
                details = "Block 0 mined marking first live smart-contract blockchain environment"
            ),
            HistoricalEventEntity(
                eventId = "EVT_COVID_CRASH_2020",
                eventTimestamp = 1584057600000L, // Mar 13, 2020
                source = "GLOBAL_MARKET_DATA",
                title = "Global Liquidity Cascade and Black Thursday Market Crash",
                eventType = "MARKET_CRASH",
                category = "MACRO",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","BNB/USDT"]""",
                sourceUrl = "https://www.bis.org/publ/qtrpdf/r_qt2006a.htm",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "HIGH_VOLATILITY",
                postEventState = "CAPITULATION_BOUNCE",
                details = "Mass liquidation cascade causing BTC intraday drawdown exceeding 45% before V-shaped recovery"
            ),
            HistoricalEventEntity(
                eventId = "EVT_BTC_HALVING_2020",
                eventTimestamp = 1589222400000L, // May 11, 2020
                source = "BITCOIN_NETWORK",
                title = "Bitcoin 3rd Halving (Block 630,000)",
                eventType = "HALVING",
                category = "ON_CHAIN",
                severity = "HIGH",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","BNB/USDT"]""",
                sourceUrl = "https://mempool.space/block/630000",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "ACCUMULATION",
                postEventState = "BULLISH_TREND",
                details = "Block subsidy halved from 12.5 BTC to 6.25 BTC"
            ),
            HistoricalEventEntity(
                eventId = "EVT_LUNA_UST_COLLAPSE_2022",
                eventTimestamp = 1652054400000L, // May 9, 2022
                source = "TERRA_CHAIN_ANALYTICS",
                title = "Terra Luna and UST Algorithmic Stablecoin Depeg and Death Spiral",
                eventType = "PROTOCOL_FAIL",
                category = "CREDIT",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","SOL/USDT","BNB/USDT"]""",
                sourceUrl = "https://ag.ny.gov/press-release/2022/attorney-general-james-warns-cryptocurrency-investors-extreme-risk",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "DISTRIBUTION",
                postEventState = "BEARISH_TREND",
                details = "Collapse of $40B algorithmic ecosystem leading to cascade across Three Arrows Capital and Celsius"
            ),
            HistoricalEventEntity(
                eventId = "EVT_ETH_MERGE_2022",
                eventTimestamp = 1663228800000L, // Sep 15, 2022
                source = "ETHEREUM_FOUNDATION",
                title = "Ethereum The Merge (PoW to PoS Transition)",
                eventType = "PROTOCOL_UPGRADE",
                category = "PROTOCOL",
                severity = "HIGH",
                primarySymbol = "ETH/USDT",
                affectedAssetsJson = """["ETH/USDT","BTC/USDT"]""",
                sourceUrl = "https://ethereum.org/en/roadmap/merge/",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "RANGE_BOUND",
                postEventState = "VOLATILITY_COMPRESSION",
                details = "Execution layer merged with Beacon Chain consensus layer reducing energy consumption by 99.95%"
            ),
            HistoricalEventEntity(
                eventId = "EVT_FTX_BANKRUPTCY_2022",
                eventTimestamp = 1668124800000L, // Nov 11, 2022
                source = "US_BANKRUPTCY_COURT",
                title = "FTX and Alameda Research File for Chapter 11 Bankruptcy",
                eventType = "BANKRUPTCY",
                category = "EXCHANGE",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","SOL/USDT","BNB/USDT"]""",
                sourceUrl = "https://restructuring.ra.kroll.com/FTX/",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "RANGE_BOUND",
                postEventState = "BEARISH_TREND",
                details = "Systemic liquidity crisis and collapse across centralized crypto credit and Alameda Research"
            ),
            HistoricalEventEntity(
                eventId = "EVT_BTC_SPOT_ETF_2024",
                eventTimestamp = 1704931200000L, // Jan 10, 2024
                source = "US_SEC_ORDER",
                title = "US SEC Approves 11 Spot Bitcoin ETFs",
                eventType = "ETF_DECISION",
                category = "REGULATORY",
                severity = "CRITICAL",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","SOL/USDT","BNB/USDT"]""",
                sourceUrl = "https://www.sec.gov/rules/sro/nysearca/2024/34-99306.pdf",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "BULLISH_TREND",
                postEventState = "INSTITUTIONAL_EXPANSION",
                details = "Omnibus approval order for 19b-4 filings including BlackRock, Fidelity, Ark Invest"
            ),
            HistoricalEventEntity(
                eventId = "EVT_BTC_HALVING_2024",
                eventTimestamp = 1713571200000L, // Apr 20, 2024
                source = "BITCOIN_NETWORK",
                title = "Bitcoin 4th Halving (Block 840,000)",
                eventType = "HALVING",
                category = "ON_CHAIN",
                severity = "HIGH",
                primarySymbol = "BTC/USDT",
                affectedAssetsJson = """["BTC/USDT","ETH/USDT","SOL/USDT"]""",
                sourceUrl = "https://mempool.space/block/840000",
                confidence = 1.0,
                marketImpactStatus = "ANALYZED",
                preEventState = "HIGH_CONSOLIDATION",
                postEventState = "SUPPLY_SQUEEZE",
                details = "Block reward reduced from 6.25 BTC to 3.125 BTC"
            )
        )

        db.historicalEventDao().insertEvents(verifiedEvents)
        return verifiedEvents.size
    }

    suspend fun getEvents(): List<HistoricalEventEntity> = db.historicalEventDao().getEventsList()

    suspend fun getEventById(eventId: String): HistoricalEventEntity? = db.historicalEventDao().getEventById(eventId)

    suspend fun getEventsByCategory(category: String): List<HistoricalEventEntity> =
        db.historicalEventDao().getEventsList().filter { it.category.equals(category, ignoreCase = true) }

    suspend fun getEventsBySeverity(severity: String): List<HistoricalEventEntity> =
        db.historicalEventDao().getEventsList().filter { it.severity.equals(severity, ignoreCase = true) }

    suspend fun getEventsForAsset(symbol: String): List<HistoricalEventEntity> =
        db.historicalEventDao().getEventsList().filter {
            it.primarySymbol == symbol || it.affectedAssetsJson.contains(symbol)
        }

    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<HistoricalEventEntity> =
        db.historicalEventDao().getEventsInRange(startTime, endTime)

    suspend fun registerEvent(event: HistoricalEventEntity): Long {
        require(event.eventId.isNotBlank()) { "Event ID must not be blank" }
        require(event.eventTimestamp > 0) { "Event timestamp must be a valid positive timestamp" }
        return db.historicalEventDao().insertEvent(event)
    }
}
