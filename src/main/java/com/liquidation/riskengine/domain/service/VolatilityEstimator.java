package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.PriceTick;
import com.liquidation.riskengine.domain.model.VolatilitySnapshot;
import com.liquidation.riskengine.domain.model.VolatilitySnapshot.EstimationMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatilityEstimator {

    static final int MIN_TICKS = 60;
    static final double DEFAULT_ANNUAL_VOL = 0.80;
    static final double SECONDS_PER_YEAR = 365.25 * 24 * 3600;
    static final double EWMA_LAMBDA = 0.94;

    private static final Duration WINDOW_1M = Duration.ofMinutes(1);
    private static final Duration WINDOW_5M = Duration.ofMinutes(5);
    private static final Duration WINDOW_1H = Duration.ofHours(1);
    private static final Duration WINDOW_24H = Duration.ofHours(24);

    private final PriceHistoryBuffer priceHistoryBuffer;

    public VolatilitySnapshot estimate(String symbol) {
        if (symbol == null) {
            return buildDefaultSnapshot("UNKNOWN");
        }
        String key = symbol.toUpperCase();
        int totalTicks = priceHistoryBuffer.size(key);

        return VolatilitySnapshot.builder()
                .symbol(key)
                .sigma1m(computeEwmaAnnualized(key, WINDOW_1M))
                .sigma5m(computeEwmaAnnualized(key, WINDOW_5M))
                .sigma1h(computeEwmaAnnualized(key, WINDOW_1H))
                .sigma24h(computeEwmaAnnualized(key, WINDOW_24H))
                .method(EstimationMethod.EWMA)
                .sampleCount(totalTicks)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public double estimateForWindow(String symbol, Duration window) {
        if (symbol == null || window == null) return DEFAULT_ANNUAL_VOL;
        return computeEwmaAnnualized(symbol.toUpperCase(), window);
    }

    private double computeEwmaAnnualized(String symbol, Duration window) {
        List<PriceTick> ticks = priceHistoryBuffer.getRecentPrices(symbol, window);
        if (ticks.size() < MIN_TICKS) {
            log.debug("[Volatility] 데이터 부족: symbol={}, window={}, ticks={}, 기본값 사용",
                    symbol, window, ticks.size());
            return DEFAULT_ANNUAL_VOL;
        }

        double[] logReturns = computeLogReturns(ticks);
        if (logReturns.length == 0) return DEFAULT_ANNUAL_VOL;

        double avgIntervalSec = computeAvgIntervalSec(ticks);
        double periodsPerYear = SECONDS_PER_YEAR / avgIntervalSec;

        double ewmaVariance = computeEwmaVariance(logReturns);
        double annualized = Math.sqrt(ewmaVariance * periodsPerYear);

        log.debug("[Volatility] EWMA: symbol={}, window={}, ticks={}, σ_annual={:.4f}",
                symbol, window, ticks.size(), annualized);
        return annualized;
    }

   private double computeEwmaVariance(double[] logReturns) {
        double variance = logReturns[0] * logReturns[0];
        for (int i = 1; i < logReturns.length; i++) {
            double r = logReturns[i];
            variance = EWMA_LAMBDA * variance + (1.0 - EWMA_LAMBDA) * r * r;
        }
        return variance;
    }

    private double[] computeLogReturns(List<PriceTick> ticks) {
        double[] returns = new double[ticks.size() - 1];
        int valid = 0;
        for (int i = 1; i < ticks.size(); i++) {
            double prev = ticks.get(i - 1).price().doubleValue();
            double curr = ticks.get(i).price().doubleValue();
            if (prev > 0 && curr > 0) {
                returns[valid++] = Math.log(curr / prev);
            }
        }
        if (valid < returns.length) {
            double[] trimmed = new double[valid];
            System.arraycopy(returns, 0, trimmed, 0, valid);
            return trimmed;
        }
        return returns;
    }

    private double computeAvgIntervalSec(List<PriceTick> ticks) {
        long spanMs = ticks.getLast().timestamp() - ticks.getFirst().timestamp();
        double avgMs = (double) spanMs / (ticks.size() - 1);
        return Math.max(avgMs / 1000.0, 0.001);
    }

    private VolatilitySnapshot buildDefaultSnapshot(String symbol) {
        return VolatilitySnapshot.builder()
                .symbol(symbol)
                .sigma1m(DEFAULT_ANNUAL_VOL)
                .sigma5m(DEFAULT_ANNUAL_VOL)
                .sigma1h(DEFAULT_ANNUAL_VOL)
                .sigma24h(DEFAULT_ANNUAL_VOL)
                .method(EstimationMethod.EWMA)
                .sampleCount(0)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
