package com.liquidation.riskengine.domain.service.liquidation;

import com.liquidation.riskengine.domain.model.MaintenanceMarginTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LiquidationPriceCalculator {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private static final Map<Integer, Double> PRIOR_WEIGHTS = Map.ofEntries(
            Map.entry(125, 0.03),
            Map.entry(100, 0.05),
            Map.entry(75,  0.07),
            Map.entry(50,  0.18),
            Map.entry(25,  0.22),
            Map.entry(20,  0.15),
            Map.entry(10,  0.16),
            Map.entry(5,   0.08),
            Map.entry(4,   0.03),
            Map.entry(3,   0.02),
            Map.entry(2,   0.01)
    );

    public BigDecimal calculateLongLiquidationPrice(
            BigDecimal entryPrice, int leverage, BigDecimal quantity, String symbol) {

        BigDecimal notional = entryPrice.multiply(quantity, MC);
        MaintenanceMarginTier tier = MaintenanceMarginTier.findTier(notional, symbol);

        BigDecimal imr = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), MC);
        BigDecimal mmr = tier.maintenanceMarginRate();

        BigDecimal factor = BigDecimal.ONE.subtract(imr, MC).add(mmr, MC);
        return entryPrice.multiply(factor, MC).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateShortLiquidationPrice(
            BigDecimal entryPrice, int leverage, BigDecimal quantity, String symbol) {

        BigDecimal notional = entryPrice.multiply(quantity, MC);
        MaintenanceMarginTier tier = MaintenanceMarginTier.findTier(notional, symbol);

        BigDecimal imr = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), MC);
        BigDecimal mmr = tier.maintenanceMarginRate();

        BigDecimal factor = BigDecimal.ONE.add(imr, MC).subtract(mmr, MC);
        return entryPrice.multiply(factor, MC).setScale(2, RoundingMode.HALF_UP);
    }

    public List<EstimatedLiquidation> estimateDistribution(
            BigDecimal currentPrice, String symbol, BigDecimal totalOi) {

        List<MaintenanceMarginTier> tiers = MaintenanceMarginTier.getTiersForSymbol(symbol);
        List<EstimatedLiquidation> results = new ArrayList<>();

        for (MaintenanceMarginTier tier : tiers) {
            int leverage = tier.maxLeverage();
            if (leverage <= 1) continue;

            BigDecimal imr = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), MC);
            BigDecimal mmr = tier.maintenanceMarginRate();

            BigDecimal longFactor = BigDecimal.ONE.subtract(imr, MC).add(mmr, MC);
            BigDecimal longLiqPrice = currentPrice.multiply(longFactor, MC)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal shortFactor = BigDecimal.ONE.add(imr, MC).subtract(mmr, MC);
            BigDecimal shortLiqPrice = currentPrice.multiply(shortFactor, MC)
                    .setScale(2, RoundingMode.HALF_UP);

            double weight = PRIOR_WEIGHTS.getOrDefault(leverage, 0.0);

            BigDecimal estimatedVolume = BigDecimal.ZERO;
            BigDecimal estimatedNotional = BigDecimal.ZERO;
            if (totalOi != null && totalOi.compareTo(BigDecimal.ZERO) > 0) {
                estimatedVolume = totalOi.multiply(BigDecimal.valueOf(weight), MC)
                        .setScale(8, RoundingMode.HALF_UP);
                estimatedNotional = estimatedVolume.multiply(currentPrice, MC)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            results.add(new EstimatedLiquidation(
                    leverage, longLiqPrice, shortLiqPrice,
                    weight, estimatedVolume, estimatedNotional, tier));

            log.debug("[LiqCalc-fallback] {} lev={}x long={} short={} weight={}%",
                    symbol, leverage, longLiqPrice, shortLiqPrice,
                    String.format("%.2f", weight * 100));
        }

        return results;
    }

    public List<EstimatedLiquidation> estimateDistribution(BigDecimal currentPrice, String symbol) {
        return estimateDistribution(currentPrice, symbol, null);
    }

    public record EstimatedLiquidation(
            int leverage,
            BigDecimal longLiquidationPrice,
            BigDecimal shortLiquidationPrice,
            double weight,
            BigDecimal estimatedVolume,
            BigDecimal estimatedNotional,
            MaintenanceMarginTier tier
    ) {}
}
