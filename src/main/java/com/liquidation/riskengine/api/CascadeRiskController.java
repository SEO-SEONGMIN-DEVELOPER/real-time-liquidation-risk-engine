package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.domain.service.CascadeRiskCalculator;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import com.liquidation.riskengine.infra.redis.service.RedisTimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CascadeRiskController {

    private final CascadeRiskCalculator cascadeRiskCalculator;
    private final RiskStateManager riskStateManager;
    private final RedisTimeSeriesService redisTimeSeriesService;

    @GetMapping("/cascade")
    public ResponseEntity<CascadeRiskReport> getCascadeRisk(
            @RequestParam String symbol,
            @RequestParam BigDecimal currentPrice,
            @RequestParam BigDecimal userLiquidationPrice,
            @RequestParam(defaultValue = "LONG") String positionSide) {

        CascadeRiskReport report = cascadeRiskCalculator.analyzeDistance(
                currentPrice, userLiquidationPrice, positionSide, symbol);

        OrderBookSnapshot orderBook = riskStateManager.getLatestOrderBook(symbol);
        if (orderBook != null) {
            cascadeRiskCalculator.analyzeOrderBookDensity(report, orderBook);
        }

        OpenInterestSnapshot latestOi = riskStateManager.getLatestOpenInterest(symbol);
        BigDecimal totalOi = latestOi != null ? latestOi.getOpenInterest() : null;
        cascadeRiskCalculator.mapLiquidationClusters(report, totalOi);

        List<LiquidationEvent> recentLiqs = redisTimeSeriesService.getRecentLiquidationEvents(
                symbol, Duration.ofMinutes(30));
        cascadeRiskCalculator.analyzeMarketPressure(report, latestOi, recentLiqs, orderBook, positionSide);

        cascadeRiskCalculator.synthesize(report);

        return ResponseEntity.ok(report);
    }
}
