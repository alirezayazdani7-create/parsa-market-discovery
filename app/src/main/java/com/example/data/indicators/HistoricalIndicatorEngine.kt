package com.example.data.indicators

import com.example.data.AppDatabase
import com.example.data.entity.HistoricalCandleEntity
import com.example.data.entity.HistoricalIndicatorSnapshotEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class HistoricalIndicatorEngine(private val db: AppDatabase) {

    /**
     * Calculates indicators strictly using candles up to [asOfTime].
     * Any candle with openTime > [asOfTime] is strictly forbidden (zero future leakage).
     */
    suspend fun calculateSnapshot(
        symbol: String,
        timeframe: String,
        candles: List<HistoricalCandleEntity>,
        asOfTime: Long
    ): HistoricalIndicatorSnapshotEntity {
        // Enforce anti-future-leakage invariant
        val filteredCandles = candles.filter { it.openTime <= asOfTime }.sortedBy { it.openTime }
        require(filteredCandles.isNotEmpty()) { "Cannot calculate indicators without historical data" }

        val closes = filteredCandles.map { it.closePrice }
        val highs = filteredCandles.map { it.highPrice }
        val lows = filteredCandles.map { it.lowPrice }
        val volumes = filteredCandles.map { it.volume }

        val sma20 = calculateSMA(closes, 20)
        val ema20 = calculateEMA(closes, 20)
        val wma20 = calculateWMA(closes, 20)
        val rsi14 = calculateRSI(closes, 14)
        val macdTuple = calculateMACD(closes, 12, 26, 9)
        val bbTuple = calculateBollingerBands(closes, 20, 2.0)
        val atr14 = calculateATR(filteredCandles, 14)
        val adx14 = calculateADX(filteredCandles, 14)
        val stochTuple = calculateStochastic(highs, lows, closes, 14, 3)
        val cci20 = calculateCCI(filteredCandles, 20)
        val roc12 = calculateROC(closes, 12)
        val vwap = calculateVWAP(filteredCandles)
        val obv = calculateOBV(filteredCandles)

        val volatility = calculateVolatility(closes, 20)
        val momentum = calculateMomentum(closes, 10)
        val trendStrength = adx14 ?: if (sma20 != null && closes.isNotEmpty()) abs(closes.last() - sma20) / sma20 * 100.0 else 0.0

        val (support, resistance) = calculateSupportResistance(highs, lows, 20)

        val snapshot = HistoricalIndicatorSnapshotEntity(
            symbol = symbol,
            timeframe = timeframe,
            timestamp = asOfTime,
            sma20 = sma20,
            ema20 = ema20,
            wma20 = wma20,
            rsi14 = rsi14,
            macdLine = macdTuple?.first,
            macdSignal = macdTuple?.second,
            macdHist = macdTuple?.third,
            bbUpper = bbTuple?.first,
            bbMiddle = bbTuple?.second,
            bbLower = bbTuple?.third,
            atr14 = atr14,
            adx14 = adx14,
            stochK = stochTuple?.first,
            stochD = stochTuple?.second,
            cci20 = cci20,
            roc12 = roc12,
            vwap = vwap,
            obv = obv,
            volatility = volatility,
            momentum = momentum,
            trendStrength = trendStrength,
            supportLevel = support,
            resistanceLevel = resistance
        )

        db.historicalIndicatorDao().insertSnapshot(snapshot)
        return snapshot
    }

    companion object {
        fun calculateSMA(data: List<Double>, period: Int): Double? {
            if (data.size < period) return null
            return data.takeLast(period).average()
        }

        fun calculateEMA(data: List<Double>, period: Int): Double? {
            if (data.size < period) return null
            val k = 2.0 / (period + 1)
            var ema = data.take(period).average()
            for (i in period until data.size) {
                ema = (data[i] * k) + (ema * (1 - k))
            }
            return ema
        }

        fun calculateWMA(data: List<Double>, period: Int): Double? {
            if (data.size < period) return null
            val slice = data.takeLast(period)
            val denominator = period * (period + 1) / 2.0
            var numerator = 0.0
            for (i in 0 until period) {
                numerator += slice[i] * (i + 1)
            }
            return numerator / denominator
        }

        fun calculateRSI(data: List<Double>, period: Int = 14): Double? {
            if (data.size <= period) return null
            var gain = 0.0
            var loss = 0.0

            for (i in 1..period) {
                val diff = data[i] - data[i - 1]
                if (diff >= 0) gain += diff else loss += -diff
            }

            var avgGain = gain / period
            var avgLoss = loss / period

            for (i in (period + 1) until data.size) {
                val diff = data[i] - data[i - 1]
                val currentGain = if (diff > 0) diff else 0.0
                val currentLoss = if (diff < 0) -diff else 0.0
                avgGain = (avgGain * (period - 1) + currentGain) / period
                avgLoss = (avgLoss * (period - 1) + currentLoss) / period
            }

            if (avgLoss == 0.0) return 100.0
            val rs = avgGain / avgLoss
            return 100.0 - (100.0 / (1.0 + rs))
        }

        fun calculateMACD(data: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Triple<Double, Double, Double>? {
            if (data.size < slow + signal) return null
            val kFast = 2.0 / (fast + 1)
            val kSlow = 2.0 / (slow + 1)

            var emaFast = data.take(fast).average()
            for (i in fast until data.size) {
                emaFast = (data[i] * kFast) + (emaFast * (1 - kFast))
            }

            var emaSlow = data.take(slow).average()
            val macdLineSeries = mutableListOf<Double>()
            for (i in slow until data.size) {
                emaSlow = (data[i] * kSlow) + (emaSlow * (1 - kSlow))
                val fastSlice = data.take(i + 1)
                val currentFastEma = calculateEMA(fastSlice, fast) ?: emaFast
                macdLineSeries.add(currentFastEma - emaSlow)
            }

            val macdLine = macdLineSeries.lastOrNull() ?: return null
            val macdSignal = calculateEMA(macdLineSeries, signal) ?: macdLine
            val macdHist = macdLine - macdSignal

            return Triple(macdLine, macdSignal, macdHist)
        }

        fun calculateBollingerBands(data: List<Double>, period: Int = 20, multiplier: Double = 2.0): Triple<Double, Double, Double>? {
            if (data.size < period) return null
            val slice = data.takeLast(period)
            val mean = slice.average()
            val variance = slice.map { (it - mean).pow(2) }.average()
            val stdDev = sqrt(variance)
            return Triple(mean + (multiplier * stdDev), mean, mean - (multiplier * stdDev))
        }

        fun calculateATR(candles: List<HistoricalCandleEntity>, period: Int = 14): Double? {
            if (candles.size <= period) return null
            val trs = mutableListOf<Double>()
            for (i in 1 until candles.size) {
                val current = candles[i]
                val prev = candles[i - 1]
                val tr = max(
                    current.highPrice - current.lowPrice,
                    max(abs(current.highPrice - prev.closePrice), abs(current.lowPrice - prev.closePrice))
                )
                trs.add(tr)
            }
            if (trs.size < period) return null
            return trs.takeLast(period).average()
        }

        fun calculateADX(candles: List<HistoricalCandleEntity>, period: Int = 14): Double? {
            if (candles.size < period * 2) return null
            val trs = mutableListOf<Double>()
            val plusDMs = mutableListOf<Double>()
            val minusDMs = mutableListOf<Double>()

            for (i in 1 until candles.size) {
                val curr = candles[i]
                val prev = candles[i - 1]
                val tr = max(curr.highPrice - curr.lowPrice, max(abs(curr.highPrice - prev.closePrice), abs(curr.lowPrice - prev.closePrice)))
                trs.add(tr)

                val upMove = curr.highPrice - prev.highPrice
                val downMove = prev.lowPrice - curr.lowPrice

                plusDMs.add(if (upMove > downMove && upMove > 0) upMove else 0.0)
                minusDMs.add(if (downMove > upMove && downMove > 0) downMove else 0.0)
            }

            val dxList = mutableListOf<Double>()
            for (i in (period - 1) until trs.size) {
                val trSum = trs.subList(i - period + 1, i + 1).sum()
                val plusSum = plusDMs.subList(i - period + 1, i + 1).sum()
                val minusSum = minusDMs.subList(i - period + 1, i + 1).sum()

                if (trSum > 0) {
                    val plusDI = 100.0 * (plusSum / trSum)
                    val minusDI = 100.0 * (minusSum / trSum)
                    val diSum = plusDI + minusDI
                    if (diSum > 0) {
                        dxList.add(100.0 * (abs(plusDI - minusDI) / diSum))
                    }
                }
            }

            if (dxList.size < period) return null
            return dxList.takeLast(period).average()
        }

        fun calculateStochastic(highs: List<Double>, lows: List<Double>, closes: List<Double>, kPeriod: Int = 14, dPeriod: Int = 3): Pair<Double, Double>? {
            if (closes.size < kPeriod + dPeriod) return null
            val kValues = mutableListOf<Double>()

            for (i in kPeriod..closes.size) {
                val hSlice = highs.subList(i - kPeriod, i)
                val lSlice = lows.subList(i - kPeriod, i)
                val highest = hSlice.maxOrNull() ?: 1.0
                val lowest = lSlice.minOrNull() ?: 0.0
                val close = closes[i - 1]
                val k = if (highest - lowest != 0.0) ((close - lowest) / (highest - lowest)) * 100.0 else 50.0
                kValues.add(k)
            }

            val k = kValues.lastOrNull() ?: return null
            val d = kValues.takeLast(dPeriod).average()
            return Pair(k, d)
        }

        fun calculateCCI(candles: List<HistoricalCandleEntity>, period: Int = 20): Double? {
            if (candles.size < period) return null
            val typicalPrices = candles.takeLast(period).map { (it.highPrice + it.lowPrice + it.closePrice) / 3.0 }
            val smaTP = typicalPrices.average()
            val meanDeviation = typicalPrices.map { abs(it - smaTP) }.average()
            if (meanDeviation == 0.0) return 0.0
            return (typicalPrices.last() - smaTP) / (0.015 * meanDeviation)
        }

        fun calculateROC(closes: List<Double>, period: Int = 12): Double? {
            if (closes.size <= period) return null
            val current = closes.last()
            val prev = closes[closes.size - 1 - period]
            if (prev == 0.0) return 0.0
            return ((current - prev) / prev) * 100.0
        }

        fun calculateVWAP(candles: List<HistoricalCandleEntity>): Double? {
            if (candles.isEmpty()) return null
            var cumulativeTPV = 0.0
            var cumulativeVolume = 0.0
            for (c in candles) {
                val typicalPrice = (c.highPrice + c.lowPrice + c.closePrice) / 3.0
                cumulativeTPV += typicalPrice * c.volume
                cumulativeVolume += c.volume
            }
            if (cumulativeVolume == 0.0) return candles.last().closePrice
            return cumulativeTPV / cumulativeVolume
        }

        fun calculateOBV(candles: List<HistoricalCandleEntity>): Double {
            if (candles.isEmpty()) return 0.0
            var obv = 0.0
            for (i in 1 until candles.size) {
                val current = candles[i]
                val prev = candles[i - 1]
                if (current.closePrice > prev.closePrice) {
                    obv += current.volume
                } else if (current.closePrice < prev.closePrice) {
                    obv -= current.volume
                }
            }
            return obv
        }

        fun calculateVolatility(closes: List<Double>, period: Int = 20): Double? {
            if (closes.size < period) return null
            val slice = closes.takeLast(period)
            val mean = slice.average()
            val variance = slice.map { (it - mean).pow(2) }.average()
            return sqrt(variance) / (if (mean != 0.0) mean else 1.0) * 100.0
        }

        fun calculateMomentum(closes: List<Double>, period: Int = 10): Double? {
            if (closes.size <= period) return null
            return closes.last() - closes[closes.size - 1 - period]
        }

        fun calculateSupportResistance(highs: List<Double>, lows: List<Double>, period: Int = 20): Pair<Double?, Double?> {
            if (highs.size < period || lows.size < period) return Pair(null, null)
            val support = lows.takeLast(period).minOrNull()
            val resistance = highs.takeLast(period).maxOrNull()
            return Pair(support, resistance)
        }
    }
}
