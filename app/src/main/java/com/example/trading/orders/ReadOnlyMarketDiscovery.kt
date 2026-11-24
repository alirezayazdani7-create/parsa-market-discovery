package com.example.trading.orders

import com.squareup.moshi.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A completed exchange candle. Prices and volumes remain decimal strings until analysis. */
data class MarketCandle(
    val openTimeMillis: Long,
    val closeTimeMillis: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String
)

interface ReadOnlyMarketDataSource {
    @Throws(IOException::class)
    fun fetchClosedCandles(symbol: String, interval: String, limit: Int): List<MarketCandle>
}

/**
 * Binance Spot public market-data adapter. Uses GET /api/v3/klines only.
 * No credentials, account endpoints, websocket subscriptions, or order endpoints exist here.
 */
class BinancePublicMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.binance.com"
) : ReadOnlyMarketDataSource {
    override fun fetchClosedCandles(symbol: String, interval: String, limit: Int): List<MarketCandle> {
        require(symbol.matches(Regex("[A-Z0-9]{2,20}/USDT"))) { "Unsupported symbol format" }
        require(interval in setOf("1m", "5m", "15m", "30m", "1h")) { "Unsupported interval" }
        require(limit in 20..100) { "Candle limit must be between 20 and 100" }
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        require(normalizedBaseUrl.startsWith("https://")) { "Market data base URL must use HTTPS" }
        val url = "$normalizedBaseUrl/api/v3/klines?symbol=${symbol.replace("/", "")}&interval=$interval&limit=$limit"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Market data HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty market data response")
            return parseKlines(body)
        }
    }

    private fun parseKlines(json: String): List<MarketCandle> {
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val result = mutableListOf<MarketCandle>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val fields = mutableListOf<String>()
            while (reader.hasNext()) {
                fields += when (reader.peek()) {
                    JsonReader.Token.STRING, JsonReader.Token.NUMBER -> reader.nextString()
                    JsonReader.Token.NULL -> { reader.nextNull<Unit>(); "" }
                    else -> { reader.skipValue(); "" }
                }
            }
            reader.endArray()
            if (fields.size < 7) throw IOException("Malformed kline row")
            val candle = try {
                MarketCandle(
                    openTimeMillis = fields[0].toLong(),
                    closeTimeMillis = fields[6].toLong(),
                    open = fields[1], high = fields[2], low = fields[3],
                    close = fields[4], volume = fields[5]
                )
            } catch (_: Exception) {
                throw IOException("Invalid kline field")
            }
            if (candle.openTimeMillis < 0L || candle.closeTimeMillis <= candle.openTimeMillis ||
                listOf(candle.open, candle.high, candle.low, candle.close, candle.volume)
                    .any { value -> value.toBigDecimalOrNull()?.signum()?.let { it < 0 } != false }) {
                throw IOException("Invalid candle values")
            }
            result += candle
        }
        reader.endArray()
        if (result.zipWithNext().any { (a, b) -> b.openTimeMillis <= a.openTimeMillis }) {
            throw IOException("Klines are not strictly chronological")
        }
        return result
    }
}

data class DirectionalAssessment(
    val direction: OpportunityDirection?,
    val score: Int,
    val reason: String
)

/** Deterministic momentum/volume screen; a candidate is not an entry or trade instruction. */
object ReadOnlyOpportunityAnalyzer {
    fun assess(candles: List<MarketCandle>): DirectionalAssessment {
        if (candles.size < 20) return DirectionalAssessment(null, 0, "INSUFFICIENT_HISTORY")
        val recent = candles.takeLast(5)
        val closes = recent.mapNotNull { it.close.toBigDecimalOrNull() }
        val volumes = candles.takeLast(20).mapNotNull { it.volume.toBigDecimalOrNull() }
        if (closes.size != 5 || volumes.size != 20 || closes.any { it.signum() <= 0 } ||
            volumes.any { it.signum() < 0 }) {
            return DirectionalAssessment(null, 0, "INVALID_NUMERIC_DATA")
        }
        val first = closes.first()
        val last = closes.last()
        val changePct = (last.subtract(first).divide(first, 8, java.math.RoundingMode.HALF_UP))
            .multiply(java.math.BigDecimal("100"))
        val recentVolume = volumes.takeLast(5).averageDecimal()
        val baselineVolume = volumes.dropLast(5).averageDecimal()
        if (baselineVolume.signum() <= 0) return DirectionalAssessment(null, 0, "NO_BASELINE_VOLUME")
        val volumeRatio = recentVolume.divide(baselineVolume, 4, java.math.RoundingMode.HALF_UP)
        val up = changePct >= java.math.BigDecimal("0.25") && volumeRatio >= java.math.BigDecimal("1.1")
        val down = changePct <= java.math.BigDecimal("-0.25") && volumeRatio >= java.math.BigDecimal("1.1")
        if (!up && !down) return DirectionalAssessment(null, 0, "NO_DIRECTIONAL_SETUP")
        val aligned = if (up) closes.zipWithNext().count { it.second >= it.first }
            else closes.zipWithNext().count { it.second <= it.first }
        val score = (5 + aligned + if (volumeRatio >= java.math.BigDecimal("1.5")) 1 else 0).coerceIn(1, 10)
        return DirectionalAssessment(
            if (up) OpportunityDirection.LONG else OpportunityDirection.SHORT,
            score,
            "5_CANDLE_MOMENTUM_VOLUME_CONFIRMATION"
        )
    }

    private fun List<java.math.BigDecimal>.averageDecimal(): java.math.BigDecimal =
        fold(java.math.BigDecimal.ZERO) { total, value -> total.add(value) }
            .divide(java.math.BigDecimal(size), 8, java.math.RoundingMode.HALF_UP)
}

/**
 * Polling scanner with an injected scheduler for deterministic tests.
 * Network errors are reported through onStatus and never converted into opportunities.
 */
class ReadOnlyContinuousMarketScanner(
    private val source: ReadOnlyMarketDataSource,
    private val symbols: List<String>,
    private val interval: String = "5m",
    private val pollEveryMillis: Long = 15_000L,
    private val initialDelayMillis: Long = 0L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val onStatus: (String) -> Unit = {}
) : ContinuousOpportunityScanner {
    private val running = AtomicBoolean(false)
    @Volatile private var task: java.util.concurrent.ScheduledFuture<*>? = null
    @Volatile private var callback: ((TradeOpportunity) -> Unit)? = null

    init {
        require(symbols.isNotEmpty())
        require(symbols.distinct().size == symbols.size)
        require(pollEveryMillis >= 1_000L)
        require(initialDelayMillis >= 0L)
        require(interval in setOf("1m", "5m", "15m", "30m", "1h"))
        symbols.forEach { require(it.matches(Regex("[A-Z0-9]{2,20}/USDT"))) }
    }

    override fun start(onOpportunity: (TradeOpportunity) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        callback = onOpportunity
        task = scheduler.scheduleWithFixedDelay({ scanOnce() }, initialDelayMillis, pollEveryMillis, TimeUnit.MILLISECONDS)
    }

    fun scanOnce() {
        if (!running.get()) return
        symbols.forEach { symbol ->
            try {
                val candles = source.fetchClosedCandles(symbol, interval, 30)
                validateFreshClosedCandles(candles, nowMillis(), interval)
                val assessment = ReadOnlyOpportunityAnalyzer.assess(candles)
                val direction = assessment.direction ?: return@forEach
                val latest = candles.last()
                val ttl = intervalMillis(interval) * 2L
                callback?.invoke(
                    TradeOpportunity(
                        opportunityId = "$symbol:$interval:${latest.openTimeMillis}:${direction.name}",
                        symbol = symbol,
                        direction = direction,
                        score = assessment.score,
                        detectedAtMillis = nowMillis(),
                        expiresAtMillis = nowMillis() + ttl
                    )
                )
                onStatus("OK:$symbol")
            } catch (error: Exception) {
                onStatus("DATA_UNAVAILABLE:$symbol:${error.javaClass.simpleName}")
            }
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        task?.cancel(true)
        task = null
        callback = null
    }

    fun shutdown() {
        stop()
        scheduler.shutdownNow()
    }

    fun isRunning(): Boolean = running.get()

    companion object {
        fun validateFreshClosedCandles(candles: List<MarketCandle>, now: Long, interval: String) {
            require(candles.size >= 20) { "Insufficient candles" }
            val step = intervalMillis(interval)
            require(candles.zipWithNext().all { (a, b) ->
                b.openTimeMillis > a.openTimeMillis && b.openTimeMillis - a.openTimeMillis == step
            }) { "Missing, duplicate, or out-of-order candles" }
            val last = candles.last()
            require(last.closeTimeMillis < now) { "Latest candle is not closed" }
            require(now - last.closeTimeMillis <= step * 2L) { "Market data is stale" }
        }

        fun intervalMillis(interval: String): Long = when (interval) {
            "1m" -> 60_000L; "5m" -> 300_000L; "15m" -> 900_000L
            "30m" -> 1_800_000L; "1h" -> 3_600_000L
            else -> throw IllegalArgumentException("Unsupported interval")
        }
    }
}

/** Independent fresh-data recheck. A pass only means eligible for further analysis. */
class FreshDataHunterReviewGateway(
    private val source: ReadOnlyMarketDataSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val interval: String = "5m"
) : HunterReviewGateway {
    override fun reviewLong(opportunity: TradeOpportunity): HunterReviewResult =
        review(opportunity, OpportunityDirection.LONG)

    override fun reviewShort(opportunity: TradeOpportunity): HunterReviewResult =
        review(opportunity, OpportunityDirection.SHORT)

    private fun review(opportunity: TradeOpportunity, expected: OpportunityDirection): HunterReviewResult {
        fun reject(reason: String) = HunterReviewResult(opportunity.opportunityId, expected, false, reason)
        if (opportunity.direction != expected) return reject("DIRECTION_MISMATCH")
        if (nowMillis() >= opportunity.expiresAtMillis) return reject("OPPORTUNITY_EXPIRED")
        return try {
            val candles = source.fetchClosedCandles(opportunity.symbol, interval, 30)
            ReadOnlyContinuousMarketScanner.validateFreshClosedCandles(candles, nowMillis(), interval)
            val assessment = ReadOnlyOpportunityAnalyzer.assess(candles)
            if (assessment.direction != expected) reject("FRESH_DATA_DIRECTION_NOT_CONFIRMED")
            else if (assessment.score < 7) reject("FRESH_DATA_SCORE_BELOW_THRESHOLD")
            else HunterReviewResult(
                opportunity.opportunityId, expected, true,
                "FRESH_DATA_CONFIRMED_FOR_FURTHER_ANALYSIS_ONLY"
            )
        } catch (error: Exception) {
            reject("FRESH_DATA_REVIEW_FAILED:${error.javaClass.simpleName}")
        }
    }
}
