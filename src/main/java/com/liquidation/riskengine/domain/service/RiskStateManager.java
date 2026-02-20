package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.domain.model.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RiskStateManager {

    private static final int MAX_LIQUIDATIONS_PER_SYMBOL = 500;

    private final Map<String, OrderBookSnapshot> latestOrderBooks = new ConcurrentHashMap<>();
    private final Map<String, OpenInterestSnapshot> latestOiSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Deque<LiquidationEvent>> recentLiquidations = new ConcurrentHashMap<>();
    private final Map<String, UserPosition> userPositions = new ConcurrentHashMap<>();

    public void registerPosition(UserPosition position) {
        if (position == null || position.getSymbol() == null) return;
        String key = position.getSymbol().toUpperCase();
        UserPosition normalized = UserPosition.builder()
                .symbol(key)
                .liquidationPrice(position.getLiquidationPrice())
                .positionSide(position.getPositionSide())
                .leverage(position.getLeverage())
                .build();
        userPositions.put(key, normalized);
        log.info("[RiskState] 포지션 등록: symbol={}, liqPrice={}, side={}, leverage={}x",
                key, position.getLiquidationPrice(), position.getPositionSide(), position.getLeverage());
    }

    public void removePosition(String symbol) {
        if (symbol == null) return;
        userPositions.remove(symbol.toUpperCase());
    }

    public UserPosition getPosition(String symbol) {
        if (symbol == null) return null;
        return userPositions.get(symbol.toUpperCase());
    }

    public Collection<UserPosition> getAllPositions() {
        return Collections.unmodifiableCollection(userPositions.values());
    }

    public void updateOrderBook(OrderBookSnapshot snapshot) {
        if (snapshot == null || snapshot.getSymbol() == null) return;
        String symbol = snapshot.getSymbol().toUpperCase();
        latestOrderBooks.put(symbol, snapshot);
    }

    public void updateOpenInterest(OpenInterestSnapshot snapshot) {
        if (snapshot == null || snapshot.getSymbol() == null) return;
        String symbol = snapshot.getSymbol().toUpperCase();
        latestOiSnapshots.put(symbol, snapshot);
    }

    public void addLiquidation(LiquidationEvent event) {
        if (event == null || event.getSymbol() == null) return;
        String symbol = event.getSymbol().toUpperCase();

        Deque<LiquidationEvent> deque = recentLiquidations
                .computeIfAbsent(symbol, k -> new ArrayDeque<>());

        deque.addLast(event);

        while (deque.size() > MAX_LIQUIDATIONS_PER_SYMBOL) {
            deque.pollFirst();
        }
    }

    public OrderBookSnapshot getLatestOrderBook(String symbol) {
        if (symbol == null) return null;
        return latestOrderBooks.get(symbol.toUpperCase());
    }

    public OpenInterestSnapshot getLatestOpenInterest(String symbol) {
        if (symbol == null) return null;
        return latestOiSnapshots.get(symbol.toUpperCase());
    }

    public List<LiquidationEvent> getRecentLiquidations(String symbol, Duration window) {
        if (symbol == null) return Collections.emptyList();
        Deque<LiquidationEvent> deque = recentLiquidations.get(symbol.toUpperCase());
        if (deque == null || deque.isEmpty()) return Collections.emptyList();

        long cutoff = Instant.now().toEpochMilli() - window.toMillis();
        return deque.stream()
                .filter(e -> e.getTimestamp() >= cutoff)
                .toList();
    }

    public List<LiquidationEvent> getAllRecentLiquidations(String symbol) {
        if (symbol == null) return Collections.emptyList();
        Deque<LiquidationEvent> deque = recentLiquidations.get(symbol.toUpperCase());
        if (deque == null) return Collections.emptyList();
        return List.copyOf(deque);
    }
}
