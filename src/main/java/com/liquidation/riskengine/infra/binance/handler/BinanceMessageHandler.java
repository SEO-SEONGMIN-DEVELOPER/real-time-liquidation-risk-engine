package com.liquidation.riskengine.infra.binance.handler;

public interface BinanceMessageHandler {

    boolean supports(String streamName);

    void handle(String rawJson);
}
