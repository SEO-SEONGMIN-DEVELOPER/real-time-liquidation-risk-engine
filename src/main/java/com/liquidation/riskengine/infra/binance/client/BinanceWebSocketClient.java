package com.liquidation.riskengine.infra.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.RingBuffer;
import com.liquidation.riskengine.infra.binance.config.BinanceProperties;
import com.liquidation.riskengine.infra.disruptor.event.EventType;
import com.liquidation.riskengine.infra.disruptor.event.MarketDataEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    private final OkHttpClient okHttpClient;
    private final BinanceProperties properties;
    private final ObjectMapper objectMapper;
    private final RingBuffer<MarketDataEvent> marketDataRingBuffer;

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        connect();
    }

    @PreDestroy
    public void destroy() {
        shutdownRequested.set(true);
        scheduler.shutdown();
        if (webSocket != null) {
            webSocket.close(1000, "Application shutting down");
        }
        log.info("[Binance WS] 종료 완료");
    }

    public void connect() {
        String url = properties.buildCombinedStreamUrl();
        log.info("[Binance WS] 연결 시도: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new BinanceWebSocketListener());
    }

    private void scheduleReconnect() {
        if (shutdownRequested.get()) {
            return;
        }

        int attempt = reconnectCount.incrementAndGet();
        int maxAttempts = properties.getMaxReconnectAttempts();

        if (maxAttempts > 0 && attempt > maxAttempts) {
            log.error("[Binance WS] 최대 재연결 횟수 초과 ({}회). 재연결 중단.", maxAttempts);
            return;
        }

        long delay = Math.min(
                properties.getReconnectIntervalMs() * attempt,
                60_000 
        );

        log.info("[Binance WS] {}ms 후 재연결 시도 ({}회차)", delay, attempt);

        scheduler.schedule(() -> {
            if (!shutdownRequested.get() && !connected.get()) {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void routeMessage(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);

            if (root.has("stream") && root.has("data")) {
                String streamName = root.get("stream").asText();
                String dataJson = root.get("data").toString();
                EventType eventType = EventType.fromStream(streamName);

                if (eventType == EventType.UNKNOWN) {
                    log.debug("[Binance WS] 미지원 스트림: {}", streamName);
                    return;
                }

                String symbol = extractSymbol(streamName);

                marketDataRingBuffer.publishEvent((event, sequence) -> {
                    event.clear();
                    event.setType(eventType);
                    event.setSymbol(symbol);
                    event.setRawJson(dataJson);
                    event.setIngestNanoTime(System.nanoTime());
                });

                log.debug("[Binance WS] RingBuffer publish: type={}, symbol={}, seq={}",
                        eventType, symbol, marketDataRingBuffer.getCursor());
            } else {
                log.debug("[Binance WS] 알 수 없는 메시지 형식: {}", text);
            }
        } catch (Exception e) {
            log.error("[Binance WS] 메시지 라우팅 실패: {}", text, e);
        }
    }

    private String extractSymbol(String streamName) {
        int atIndex = streamName.indexOf('@');
        if (atIndex > 0) {
            return streamName.substring(0, atIndex).toUpperCase();
        }
        return streamName.toUpperCase();
    }

    private class BinanceWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
            connected.set(true);
            reconnectCount.set(0);
            log.info("[Binance WS] 연결 성공 (code={})", response.code());
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
            routeMessage(text);
        }

        @Override
        public void onClosing(@NotNull WebSocket ws, int code, @NotNull String reason) {
            log.info("[Binance WS] 서버 연결 종료 요청 (code={}, reason={})", code, reason);
            ws.close(code, reason);
        }

        @Override
        public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
            connected.set(false);
            log.info("[Binance WS] 연결 종료 (code={}, reason={})", code, reason);
            scheduleReconnect();
        }

        @Override
        public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
            connected.set(false);
            log.error("[Binance WS] 연결 실패: {}", t.getMessage(), t);
            scheduleReconnect();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}
