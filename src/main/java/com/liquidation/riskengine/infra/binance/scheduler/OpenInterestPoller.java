package com.liquidation.riskengine.infra.binance.scheduler;

import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.infra.binance.client.BinanceRestClient;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import com.liquidation.riskengine.infra.redis.service.RedisTimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenInterestPoller {

    private final BinanceRestClient restClient;
    private final BinanceProperties properties;
    private final RedisTimeSeriesService redisTimeSeriesService;

    private final Map<String, BigDecimal> previousOiMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${binance.open-interest-poll-interval-ms:3000}")
    public void pollOpenInterest() {
        for (String symbol : properties.getSymbols()) {
            restClient.getOpenInterest(symbol).ifPresent(response -> {
                String upperSymbol = symbol.toUpperCase();
                BigDecimal currentOi = response.getOpenInterest();
                BigDecimal previousOi = previousOiMap.get(upperSymbol);

                BigDecimal change = BigDecimal.ZERO;
                BigDecimal changePercent = BigDecimal.ZERO;

                if (previousOi != null && previousOi.compareTo(BigDecimal.ZERO) > 0) {
                    change = currentOi.subtract(previousOi);
                    changePercent = change.divide(previousOi, new MathContext(6, RoundingMode.HALF_UP))
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }

                OpenInterestSnapshot snapshot = OpenInterestSnapshot.builder()
                        .symbol(upperSymbol)
                        .openInterest(currentOi)
                        .previousOpenInterest(previousOi)
                        .change(change)
                        .changePercent(changePercent)
                        .timestamp(Instant.now().toEpochMilli())
                        .build();

                redisTimeSeriesService.saveOpenInterestSnapshot(snapshot);
                previousOiMap.put(upperSymbol, currentOi);

                log.info("[OI Poll] symbol={}, oi={}, change={}, changePercent={}% → Redis 저장 완료",
                        upperSymbol, currentOi, change, changePercent);
            });
        }
    }
}
