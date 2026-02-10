package com.liquidation.riskengine.infra.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {

    private String wsBaseUrl = "wss://fstream.binance.com";

    private List<String> symbols = List.of("btcusdt", "ethusdt");

    private List<String> streams = List.of("markPrice");

    private int markPriceSpeed = 1000;

    private long reconnectIntervalMs = 5000;

    private int maxReconnectAttempts = 0;

    public String buildCombinedStreamUrl() {
        StringBuilder sb = new StringBuilder(wsBaseUrl).append("/stream?streams=");
        List<String> streamPaths = symbols.stream()
                .flatMap(symbol -> streams.stream()
                        .map(stream -> buildStreamName(symbol, stream)))
                .toList();
        sb.append(String.join("/", streamPaths));
        return sb.toString();
    }

    private String buildStreamName(String symbol, String stream) {
        if ("markPrice".equals(stream)) {
            String speed = markPriceSpeed == 1000 ? "@1s" : "";
            return symbol.toLowerCase() + "@markPrice" + speed;
        }
        return symbol.toLowerCase() + "@" + stream;
    }
}
