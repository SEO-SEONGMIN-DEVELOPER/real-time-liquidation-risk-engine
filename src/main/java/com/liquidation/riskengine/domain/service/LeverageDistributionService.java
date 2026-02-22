package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.MaintenanceMarginTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class LeverageDistributionService {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final double DECAY_FACTOR = 0.995;
    private static final long DECAY_INTERVAL_MS = 60_000;

    private final Map<String, Map<Integer, Double>> existenceSignal = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Double>> liquidatedVolume = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastDecayTime = new ConcurrentHashMap<>();

    private static final Map<Integer, Double> PRIOR_WEIGHTS = Map.ofEntries(
            Map.entry(125, 0.03),
            Map.entry(100, 0.05),
            Map.entry(75, 0.07),
            Map.entry(50, 0.18),
            Map.entry(25, 0.22),
            Map.entry(20, 0.15),
            Map.entry(10, 0.16),
            Map.entry(5, 0.08),
            Map.entry(4, 0.03),
            Map.entry(3, 0.02),
            Map.entry(2, 0.01)
    );

    private static final double PRIOR_STRENGTH = 100.0;
    private static final double EXISTENCE_SIGNAL_WEIGHT = 1.0;
    private static final double LIQUIDATION_REDUCTION_SCALE = 5.0;

    public void recordLiquidation(LiquidationEvent event, BigDecimal markPriceAtTime) {
        if (event == null || markPriceAtTime == null || markPriceAtTime.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String symbol = event.getSymbol().toUpperCase();
        int estimatedLeverage = reverseLeverage(event, markPriceAtTime, symbol);

        if (estimatedLeverage <= 0) return;

        int tier = snapToTier(estimatedLeverage, symbol);
        if (tier <= 0) return;

        existenceSignal
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .merge(tier, EXISTENCE_SIGNAL_WEIGHT, Double::sum);

        double reductionAmount = event.getNotionalValue() != null
                ? event.getNotionalValue().doubleValue() / 10_000.0 * LIQUIDATION_REDUCTION_SCALE
                : LIQUIDATION_REDUCTION_SCALE;
        reductionAmount = Math.max(1.0, Math.min(reductionAmount, 200.0));

        liquidatedVolume
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .merge(tier, reductionAmount, Double::sum);

        applyDecayIfNeeded(symbol);

        log.debug("[LevDist] {} | 청산 관측: liqPrice={}, markPrice={}, 추정={}x→티어={}x | 신호=+{} 감소=-{}",
                symbol, event.getPrice(), markPriceAtTime, estimatedLeverage, tier,
                String.format("%.1f", EXISTENCE_SIGNAL_WEIGHT),
                String.format("%.1f", reductionAmount));
    }

    public Map<Integer, Double> getDistribution(String symbol) {
        symbol = symbol.toUpperCase();
        Map<Integer, Double> existence = existenceSignal.getOrDefault(symbol, Map.of());
        Map<Integer, Double> liquidated = liquidatedVolume.getOrDefault(symbol, Map.of());

        List<MaintenanceMarginTier> tiers = MaintenanceMarginTier.getTiersForSymbol(symbol);
        Map<Integer, Double> result = new HashMap<>();

        double totalRaw = 0;
        for (MaintenanceMarginTier tier : tiers) {
            int lev = tier.maxLeverage();
            if (lev <= 1) continue;

            double prior = PRIOR_WEIGHTS.getOrDefault(lev, 0.05) * PRIOR_STRENGTH;
            double signal = existence.getOrDefault(lev, 0.0);
            double removed = liquidated.getOrDefault(lev, 0.0);

            double adjusted = Math.max(0.1, prior + signal - removed);

            result.put(lev, adjusted);
            totalRaw += adjusted;
        }

        if (totalRaw > 0) {
            for (Map.Entry<Integer, Double> entry : result.entrySet()) {
                entry.setValue(entry.getValue() / totalRaw);
            }
        }

        double totalLiquidated = liquidated.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalLiquidated > 10) {
            log.debug("[LevDist] {} | 청산 총량={}, 분포: {}", symbol,
                    String.format("%.1f", totalLiquidated),
                    formatDistribution(result));
        }

        return result;
    }

    public double getWeight(String symbol, int leverage) {
        return getDistribution(symbol).getOrDefault(leverage, 0.0);
    }

    public BigDecimal estimateNotionalAtTier(String symbol, int leverage, BigDecimal totalOi, BigDecimal currentPrice) {
        double weight = getWeight(symbol, leverage);
        BigDecimal oiAtTier = totalOi.multiply(BigDecimal.valueOf(weight), MC);
        return oiAtTier.multiply(currentPrice, MC).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal estimateVolumeAtTier(String symbol, int leverage, BigDecimal totalOi) {
        double weight = getWeight(symbol, leverage);
        return totalOi.multiply(BigDecimal.valueOf(weight), MC).setScale(8, RoundingMode.HALF_UP);
    }

    private int reverseLeverage(LiquidationEvent event, BigDecimal markPrice, String symbol) {
        BigDecimal liqPrice = event.getPrice();
        if (liqPrice == null || liqPrice.compareTo(BigDecimal.ZERO) <= 0) return 0;

        BigDecimal ratio = liqPrice.subtract(markPrice).abs()
                .divide(markPrice, MC);

        if (ratio.compareTo(BigDecimal.ZERO) == 0) return 0;

        List<MaintenanceMarginTier> tiers = MaintenanceMarginTier.getTiersForSymbol(symbol);

        int bestLev = 0;
        BigDecimal bestDiff = BigDecimal.valueOf(999);

        for (MaintenanceMarginTier tier : tiers) {
            int lev = tier.maxLeverage();
            if (lev <= 1) continue;

            BigDecimal imr = BigDecimal.ONE.divide(BigDecimal.valueOf(lev), MC);
            BigDecimal mmr = tier.maintenanceMarginRate();

            BigDecimal expectedRatio = imr.subtract(mmr, MC);

            if (expectedRatio.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal diff = ratio.subtract(expectedRatio).abs();
            if (diff.compareTo(bestDiff) < 0) {
                bestDiff = diff;
                bestLev = lev;
            }
        }

        BigDecimal tolerance = ratio.multiply(new BigDecimal("0.5"), MC);
        if (bestDiff.compareTo(tolerance) > 0) return 0;

        return bestLev;
    }

    private int snapToTier(int estimatedLeverage, String symbol) {
        List<MaintenanceMarginTier> tiers = MaintenanceMarginTier.getTiersForSymbol(symbol);

        int closest = 0;
        int closestDiff = Integer.MAX_VALUE;

        for (MaintenanceMarginTier tier : tiers) {
            int lev = tier.maxLeverage();
            if (lev <= 1) continue;
            int diff = Math.abs(lev - estimatedLeverage);
            if (diff < closestDiff) {
                closestDiff = diff;
                closest = lev;
            }
        }

        return closest;
    }

    private void applyDecayIfNeeded(String symbol) {
        AtomicLong lastTime = lastDecayTime.computeIfAbsent(symbol, k -> new AtomicLong(System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime.get();

        if (elapsed < DECAY_INTERVAL_MS) return;

        int periods = (int) (elapsed / DECAY_INTERVAL_MS);
        double multiplier = Math.pow(DECAY_FACTOR, periods);

        Map<Integer, Double> signal = existenceSignal.get(symbol);
        if (signal != null) {
            signal.replaceAll((k, v) -> v * multiplier);
            signal.values().removeIf(v -> v < 0.01);
        }

        Map<Integer, Double> liqVol = liquidatedVolume.get(symbol);
        if (liqVol != null) {
            liqVol.replaceAll((k, v) -> v * multiplier);
            liqVol.values().removeIf(v -> v < 0.01);
        }

        lastTime.set(now);
    }

    private String formatDistribution(Map<Integer, Double> dist) {
        StringBuilder sb = new StringBuilder();
        dist.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("%dx=%.1f%% ", e.getKey(), e.getValue() * 100)));
        return sb.toString().trim();
    }
}
