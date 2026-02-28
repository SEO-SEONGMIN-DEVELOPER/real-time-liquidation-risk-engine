package com.liquidation.riskengine.infra.disruptor.event;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;

public class RiskResultEvent {

    private CascadeRiskReport report;
    private long calcNanoTime;

    public void clear() {
        report = null;
        calcNanoTime = 0L;
    }

    public CascadeRiskReport getReport() {
        return report;
    }

    public void setReport(CascadeRiskReport report) {
        this.report = report;
    }

    public long getCalcNanoTime() {
        return calcNanoTime;
    }

    public void setCalcNanoTime(long calcNanoTime) {
        this.calcNanoTime = calcNanoTime;
    }

    @Override
    public String toString() {
        return "RiskResultEvent{symbol=" + (report != null ? report.getSymbol() : "null") + "}";
    }
}
