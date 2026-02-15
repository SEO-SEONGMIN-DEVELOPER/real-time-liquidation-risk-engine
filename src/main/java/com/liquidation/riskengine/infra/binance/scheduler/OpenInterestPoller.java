package com.liquidation.riskengine.infra.binance.scheduler;

import com.liquidation.riskengine.infra.binance.client.BinanceRestClient;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenInterestPoller {

    private final BinanceRestClient restClient;
    private final BinanceProperties properties;

    @Scheduled(fixedDelayString = "${binance.open-interest-poll-interval-ms:3000}")
    public void pollOpenInterest() {
        for (String symbol : properties.getSymbols()) {
            restClient.getOpenInterest(symbol).ifPresent(response ->
                    log.info("[OI Poll] symbol={}, openInterest={}",
                            symbol.toUpperCase(), response.getOpenInterest())
            );
        }
    }
}
