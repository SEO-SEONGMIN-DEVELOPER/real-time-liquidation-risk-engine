package com.liquidation.riskengine.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MarkPriceCache {

    private final Map<String, BigDecimal> latestMarkPrice = new ConcurrentHashMap<>();

    public void update(String symbol, BigDecimal markPrice) {
        if (symbol == null || markPrice == null) return;
        latestMarkPrice.put(symbol.toUpperCase(), markPrice);
    }

    public BigDecimal get(String symbol) {
        if (symbol == null) return null;
        return latestMarkPrice.get(symbol.toUpperCase());
    }
}
