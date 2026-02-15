package com.liquidation.riskengine.infra.disruptor.event;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.infra.binance.dto.MarkPriceEvent;

public class MarketDataEvent {

    private EventType type;
    private String symbol;
    private String rawJson;
    private long ingestNanoTime;

    private MarkPriceEvent markPrice;
    private LiquidationEvent liquidationEvent;
    private OrderBookSnapshot orderBook;
    private OpenInterestSnapshot openInterest;

    public void clear() {
        type = null;
        symbol = null;
        rawJson = null;
        ingestNanoTime = 0L;
        markPrice = null;
        liquidationEvent = null;
        orderBook = null;
        openInterest = null;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public long getIngestNanoTime() {
        return ingestNanoTime;
    }

    public void setIngestNanoTime(long ingestNanoTime) {
        this.ingestNanoTime = ingestNanoTime;
    }

    public MarkPriceEvent getMarkPrice() {
        return markPrice;
    }

    public void setMarkPrice(MarkPriceEvent markPrice) {
        this.markPrice = markPrice;
    }

    public LiquidationEvent getLiquidationEvent() {
        return liquidationEvent;
    }

    public void setLiquidationEvent(LiquidationEvent liquidationEvent) {
        this.liquidationEvent = liquidationEvent;
    }

    public OrderBookSnapshot getOrderBook() {
        return orderBook;
    }

    public void setOrderBook(OrderBookSnapshot orderBook) {
        this.orderBook = orderBook;
    }

    public OpenInterestSnapshot getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(OpenInterestSnapshot openInterest) {
        this.openInterest = openInterest;
    }

    @Override
    public String toString() {
        return "MarketDataEvent{type=" + type + ", symbol=" + symbol + "}";
    }
}
