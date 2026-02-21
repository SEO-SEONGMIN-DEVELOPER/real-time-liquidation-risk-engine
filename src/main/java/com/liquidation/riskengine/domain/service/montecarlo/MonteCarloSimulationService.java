package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.model.VolatilitySnapshot;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.VolatilityEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonteCarloSimulationService {

    private final MarkPriceCache markPriceCache;
    private final VolatilityEstimator volatilityEstimator;
    private final PricePathGenerator pricePathGenerator;
    private final LiquidationDetector liquidationDetector;
    private final MonteCarloProperties properties;

    public Optional<MonteCarloReport> simulate(String symbol,
                                               BigDecimal liquidationPrice,
                                               String positionSide) {
        if (!properties.isEnabled()) {
            log.debug("[MC] 비활성 상태, 시뮬레이션 스킵: symbol={}", symbol);
            return Optional.empty();
        }

        BigDecimal currentPrice = markPriceCache.get(symbol);
        if (currentPrice == null) {
            log.warn("[MC] 현재가 없음, 시뮬레이션 불가: symbol={}", symbol);
            return Optional.empty();
        }

        long startNano = System.nanoTime();

        VolatilitySnapshot volSnap = volatilityEstimator.estimate(symbol);
        double sigma = volSnap.getSigmaForLabel(properties.getVolatilityWindow());

        SimulationRequest request = SimulationRequest.builder()
                .startPrice(currentPrice.doubleValue())
                .sigma(sigma)
                .pathCount(properties.getPathCount())
                .timeStepMinutes(properties.getTimeStepMinutes())
                .horizonMinutes(properties.maxHorizonMinutes())
                .useFatTail(properties.isUseFatTail())
                .degreesOfFreedom(properties.getDegreesOfFreedom())
                .build();

        double[][] paths = pricePathGenerator.generate(request);

        MonteCarloReport report = liquidationDetector.detect(
                symbol,
                paths,
                liquidationPrice.doubleValue(),
                positionSide,
                sigma,
                properties.getTimeStepMinutes(),
                properties.horizonsArray());

        long totalMicros = (System.nanoTime() - startNano) / 1_000;
        log.info("[MC] 시뮬레이션 완료: symbol={}, side={}, σ={:.4f}, risk={}, paths={}, total={}μs",
                symbol, positionSide, sigma, report.getRiskLevel(),
                properties.getPathCount(), totalMicros);

        return Optional.of(report);
    }
}
