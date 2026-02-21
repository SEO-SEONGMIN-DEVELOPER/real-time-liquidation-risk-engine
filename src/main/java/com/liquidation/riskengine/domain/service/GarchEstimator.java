package com.liquidation.riskengine.domain.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GarchEstimator {

    private final VolatilityProperties properties;

    @Getter
    public static class GarchResult {
        private final double currentVariance;
        private final double omega;
        private final double alpha;
        private final double beta;
        private final double unconditionalVariance;
        private final double annualizedSigma;
        private final double periodsPerYear;

        public GarchResult(double currentVariance, double omega, double alpha, double beta,
                           double periodsPerYear) {
            this.currentVariance = currentVariance;
            this.omega = omega;
            this.alpha = alpha;
            this.beta = beta;
            this.periodsPerYear = periodsPerYear;
            double persistence = alpha + beta;
            this.unconditionalVariance = persistence < 1.0
                    ? omega / (1.0 - persistence)
                    : currentVariance;
            this.annualizedSigma = Math.sqrt(currentVariance * periodsPerYear);
        }

        public double[] forecastVarianceSchedule(int steps) {
            double[] schedule = new double[steps];
            double persistence = alpha + beta;
            double varDiff = currentVariance - unconditionalVariance;
            double persistPow = 1.0;

            for (int h = 0; h < steps; h++) {
                schedule[h] = unconditionalVariance + persistPow * varDiff;
                if (schedule[h] <= 0) schedule[h] = unconditionalVariance;
                persistPow *= persistence;
            }
            return schedule;
        }

        public double[] forecastSigmaScheduleAnnualized(int steps) {
            double[] varSchedule = forecastVarianceSchedule(steps);
            double[] sigmaSchedule = new double[steps];
            for (int i = 0; i < steps; i++) {
                sigmaSchedule[i] = Math.sqrt(varSchedule[i] * periodsPerYear);
            }
            return sigmaSchedule;
        }
    }

    public GarchResult estimate(double[] logReturns, double periodsPerYear) {
        if (logReturns.length < 2) {
            return null;
        }

        double alpha, beta;

        if (properties.isGarchAutoFit()) {
            double[] fitted = fitQuasiMLE(logReturns);
            alpha = fitted[0];
            beta = fitted[1];
        } else {
            alpha = properties.getGarchAlpha();
            beta = properties.getGarchBeta();
        }

        double sampleVar = computeSampleVariance(logReturns);
        double persistence = alpha + beta;
        double omega = persistence < 1.0
                ? sampleVar * (1.0 - persistence)
                : sampleVar * 0.05;

        double currentVariance = computeGarchVarianceSeries(logReturns, omega, alpha, beta);

        log.debug("[GARCH] α={:.4f}, β={:.4f}, ω={:.8f}, σ²_t={:.8f}, σ_ann={:.4f}",
                alpha, beta, omega, currentVariance,
                Math.sqrt(currentVariance * periodsPerYear));

        return new GarchResult(currentVariance, omega, alpha, beta, periodsPerYear);
    }

    private double computeGarchVarianceSeries(double[] logReturns, double omega,
                                               double alpha, double beta) {
        double variance = logReturns[0] * logReturns[0];
        for (int i = 1; i < logReturns.length; i++) {
            double r = logReturns[i - 1];
            variance = omega + alpha * r * r + beta * variance;
        }
        return Math.max(variance, 1e-20);
    }

    private double[] fitQuasiMLE(double[] logReturns) {
        double sampleVar = computeSampleVariance(logReturns);
        double bestAlpha = properties.getGarchAlpha();
        double bestBeta = properties.getGarchBeta();
        double bestLL = Double.NEGATIVE_INFINITY;

        for (double a = 0.01; a <= 0.20; a += 0.01) {
            for (double b = 0.70; b <= 0.98; b += 0.01) {
                if (a + b >= 1.0) continue;

                double omega = sampleVar * (1.0 - a - b);
                double ll = computeLogLikelihood(logReturns, omega, a, b);

                if (ll > bestLL) {
                    bestLL = ll;
                    bestAlpha = a;
                    bestBeta = b;
                }
            }
        }

        log.debug("[GARCH] quasi-MLE 완료: α={:.3f}, β={:.3f}, LL={:.2f}",
                bestAlpha, bestBeta, bestLL);
        return new double[]{bestAlpha, bestBeta};
    }

    private double computeLogLikelihood(double[] logReturns, double omega,
                                         double alpha, double beta) {
        double variance = logReturns[0] * logReturns[0];
        double ll = 0.0;

        for (int i = 1; i < logReturns.length; i++) {
            double r = logReturns[i];
            variance = omega + alpha * logReturns[i - 1] * logReturns[i - 1] + beta * variance;
            if (variance <= 0) return Double.NEGATIVE_INFINITY;
            ll += -0.5 * (Math.log(variance) + r * r / variance);
        }
        return ll;
    }

    private double computeSampleVariance(double[] logReturns) {
        double sum = 0.0;
        for (double r : logReturns) sum += r;
        double mean = sum / logReturns.length;

        double sumSq = 0.0;
        for (double r : logReturns) {
            double diff = r - mean;
            sumSq += diff * diff;
        }
        return sumSq / (logReturns.length - 1);
    }
}
