package com.liquidation.riskengine.domain.service.state;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.domain.model.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RiskStateManager {

    private static final int MAX_LIQUIDATIONS_PER_SYMBOL = 500;

    private final Map<String, BigDecimal> latestMarkPrices = new ConcurrentHashMap<>();
    private final Map<String, OrderBookSnapshot> latestOrderBooks = new ConcurrentHashMap<>();
    private final Map<String, OpenInterestSnapshot> latestOiSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Deque<LiquidationEvent>> recentLiquidations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, UserPosition>> positionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, UserPosition>> positionsBySymbol = new ConcurrentHashMap<>();

    public void registerPosition(UserPosition position) {
        if (position == null || position.getSymbol() == null || position.getUserId() == null) return;
        String userId = normalizeUserId(position.getUserId());
        String symbol = normalizeSymbol(position.getSymbol());
        UserPosition normalized = UserPosition.builder()
                .userId(userId)
                .symbol(symbol)
                .liquidationPrice(position.getLiquidationPrice())
                .positionSide(position.getPositionSide())
                .leverage(position.getLeverage())
                .build();

        positionsByUser
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(symbol, normalized);

        positionsBySymbol
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .put(userId, normalized);

        log.info("[RiskState] 포지션 등록: userId={}, symbol={}, liqPrice={}, side={}, leverage={}x",
                userId, symbol, position.getLiquidationPrice(), position.getPositionSide(), position.getLeverage());
    }

    public void removePosition(String userId, String symbol) {
        if (userId == null || symbol == null) return;
        String normalizedUserId = normalizeUserId(userId);
        String normalizedSymbol = normalizeSymbol(symbol);

        Map<String, UserPosition> userPositions = positionsByUser.get(normalizedUserId);
        if (userPositions != null) {
            userPositions.remove(normalizedSymbol);
            if (userPositions.isEmpty()) {
                positionsByUser.remove(normalizedUserId);
            }
        }

        Map<String, UserPosition> symbolPositions = positionsBySymbol.get(normalizedSymbol);
        if (symbolPositions != null) {
            symbolPositions.remove(normalizedUserId);
            if (symbolPositions.isEmpty()) {
                positionsBySymbol.remove(normalizedSymbol);
            }
        }
    }

    public UserPosition getPosition(String userId, String symbol) {
        if (userId == null || symbol == null) return null;
        Map<String, UserPosition> userPositions = positionsByUser.get(normalizeUserId(userId));
        if (userPositions == null) return null;
        return userPositions.get(normalizeSymbol(symbol));
    }

    public Collection<UserPosition> getAllPositions() {
        List<UserPosition> all = new ArrayList<>();
        for (Map<String, UserPosition> userPositions : positionsByUser.values()) {
            all.addAll(userPositions.values());
        }
        return Collections.unmodifiableCollection(all);
    }

    public Collection<UserPosition> getPositionsByUser(String userId) {
        if (userId == null) return Collections.emptyList();
        Map<String, UserPosition> userPositions = positionsByUser.get(normalizeUserId(userId));
        if (userPositions == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(new ArrayList<>(userPositions.values()));
    }

    public Collection<UserPosition> getPositionsBySymbol(String symbol) {
        if (symbol == null) return Collections.emptyList();
        Map<String, UserPosition> symbolPositions = positionsBySymbol.get(normalizeSymbol(symbol));
        if (symbolPositions == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(new ArrayList<>(symbolPositions.values()));
    }

    public void updateOrderBook(OrderBookSnapshot snapshot) {
        if (snapshot == null || snapshot.getSymbol() == null) return;
        String symbol = snapshot.getSymbol().toUpperCase();
        latestOrderBooks.put(symbol, snapshot);
    }

    public void updateMarkPrice(String symbol, BigDecimal markPrice) {
        if (symbol == null || markPrice == null) return;
        latestMarkPrices.put(symbol.toUpperCase(), markPrice);
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

    public BigDecimal getLatestMarkPrice(String symbol) {
        if (symbol == null) return null;
        return latestMarkPrices.get(symbol.toUpperCase());
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

    private String normalizeSymbol(String symbol) {
        return symbol.toUpperCase();
    }

    private String normalizeUserId(String userId) {
        return userId.trim().toLowerCase();
    }
}
