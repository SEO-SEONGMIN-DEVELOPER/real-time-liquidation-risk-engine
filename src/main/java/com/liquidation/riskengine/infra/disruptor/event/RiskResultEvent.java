package com.liquidation.riskengine.infra.disruptor.event;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport;

public class RiskResultEvent {

    private String userId;
    private CascadeRiskReport report;
    private MonteCarloReport mcReport;
    private long calcNanoTime;

    public void clear() {
        userId = null;
        report = null;
        mcReport = null;
        calcNanoTime = 0L;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public CascadeRiskReport getReport() {
        return report;
    }

    public void setReport(CascadeRiskReport report) {
        this.report = report;
    }

    public MonteCarloReport getMcReport() {
        return mcReport;
    }

    public void setMcReport(MonteCarloReport mcReport) {
        this.mcReport = mcReport;
    }

    public long getCalcNanoTime() {
        return calcNanoTime;
    }

    public void setCalcNanoTime(long calcNanoTime) {
        this.calcNanoTime = calcNanoTime;
    }

    @Override
    public String toString() {
        String sym = report != null ? report.getSymbol()
                : (mcReport != null ? mcReport.getSymbol() : "null");
        return "RiskResultEvent{userId=" + userId + ", symbol=" + sym + ", cascade=" + (report != null)
                + ", mc=" + (mcReport != null) + "}";
    }
}
