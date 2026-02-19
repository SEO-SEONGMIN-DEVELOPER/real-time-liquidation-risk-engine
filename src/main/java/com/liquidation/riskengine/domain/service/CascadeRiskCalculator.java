package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeRiskCalculator {

    private final LiquidationPriceCalculator liquidationPriceCalculator;

    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);

    public CascadeRiskReport analyzeDistance(
            BigDecimal currentPrice,
            BigDecimal userLiquidationPrice,
            String positionSide,
            String symbol) {

        BigDecimal distance = currentPrice.subtract(userLiquidationPrice).abs();

        double distancePercent = distance
                .divide(currentPrice, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();

        String direction = resolveDirection(positionSide);

        BigDecimal priceRangeLow = currentPrice.min(userLiquidationPrice);
        BigDecimal priceRangeHigh = currentPrice.max(userLiquidationPrice);

        CascadeRiskReport report = CascadeRiskReport.builder()
                .symbol(symbol.toUpperCase())
                .currentPrice(currentPrice)
                .userLiquidationPrice(userLiquidationPrice)
                .positionSide(positionSide.toUpperCase())
                .distance(distance)
                .distancePercent(distancePercent)
                .direction(direction)
                .priceRangeLow(priceRangeLow)
                .priceRangeHigh(priceRangeHigh)
                .timestamp(Instant.now().toEpochMilli())
                .build();

        log.info("[Stage1] {} | side={} | 현재가={} | 청산가={} | 거리={} ({}%) | 방향={} | 구간=[{} ~ {}]",
                symbol, positionSide, currentPrice, userLiquidationPrice,
                distance, String.format("%.2f", distancePercent),
                direction, priceRangeLow, priceRangeHigh);

        return report;
    }

    private String resolveDirection(String positionSide) {
        if ("LONG".equalsIgnoreCase(positionSide)) {
            return "DOWN";
        }
        if ("SHORT".equalsIgnoreCase(positionSide)) {
            return "UP";
        }
        return "UNKNOWN";
    }
}
