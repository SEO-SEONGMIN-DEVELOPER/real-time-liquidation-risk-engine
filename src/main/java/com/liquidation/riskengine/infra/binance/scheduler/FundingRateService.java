package com.liquidation.riskengine.infra.binance.scheduler;

import com.liquidation.riskengine.infra.binance.client.BinanceRestClient;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingRateService {

    private final BinanceRestClient binanceRestClient;
    private final BinanceProperties binanceProperties;

    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        pollFundingRates();
    }

    @Scheduled(fixedRate = 8 * 60 * 60 * 1000)
    public void pollFundingRates() {
        for (String symbol : binanceProperties.getSymbols()) {
            try {
                binanceRestClient.getLatestFundingRate(symbol).ifPresent(fr -> {
                    double rate = Double.parseDouble(fr.getFundingRate());
                    cache.put(symbol.toUpperCase(), rate);
                    log.info("[FundingRate] 갱신: symbol={}, rate={}", symbol.toUpperCase(), rate);
                });
            } catch (Exception e) {
                log.warn("[FundingRate] 폴링 실패: symbol={}", symbol, e);
            }
        }
    }

    public double getFundingRate(String symbol) {
        if (symbol == null) return 0.0;
        return cache.getOrDefault(symbol.toUpperCase(), 0.0);
    }
}
