package com.liquidation.riskengine.infra.disruptor.event;

public enum EventType {

    MARK_PRICE,
    FORCE_ORDER,
    ORDER_BOOK,
    OI_UPDATE,
    UNKNOWN;

    public static EventType fromStream(String streamName) {
        if (streamName == null) return UNKNOWN;
        if (streamName.contains("@markPrice")) return MARK_PRICE;
        if (streamName.contains("@forceOrder")) return FORCE_ORDER;
        if (streamName.contains("@depth")) return ORDER_BOOK;
        return UNKNOWN;
    }

    public boolean isHighPriority() {
        return this == FORCE_ORDER;
    }

    public boolean triggersRiskCalc() {
        return this == MARK_PRICE || this == FORCE_ORDER;
    }
}
