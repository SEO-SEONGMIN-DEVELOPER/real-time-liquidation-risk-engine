package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.MaintenanceMarginTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LiquidationPriceCalculator {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

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

    public List<EstimatedLiquidation> estimateDistribution(BigDecimal currentPrice, String symbol) {
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

            double weight = estimateWeight(leverage);

            results.add(new EstimatedLiquidation(
                    leverage, longLiqPrice, shortLiqPrice, weight, tier));

            log.debug("[LiqCalc] {}  lev={}x  long={}  short={}  mmr={}  weight={}",
                    symbol, leverage, longLiqPrice, shortLiqPrice, mmr, weight);
        }

        return results;
    }

    private double estimateWeight(int leverage) {
        return switch (leverage) {
            case 125 -> 0.03;
            case 100 -> 0.05;
            case 75  -> 0.07;
            case 50  -> 0.18;
            case 25  -> 0.22;
            case 20  -> 0.15;
            case 10  -> 0.16;
            case 5   -> 0.08;
            case 4   -> 0.03;
            case 3   -> 0.02;
            case 2   -> 0.01;
            default  -> 0.05;
        };
    }

    public record EstimatedLiquidation(
            int leverage,
            BigDecimal longLiquidationPrice,
            BigDecimal shortLiquidationPrice,
            double weight,
            MaintenanceMarginTier tier
    ) {}
}
