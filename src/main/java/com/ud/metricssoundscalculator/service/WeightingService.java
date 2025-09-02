package com.ud.metricssoundscalculator.service;

import org.springframework.stereotype.Service;

@Service
public class WeightingService {

    public double[] applyAWeighting(double[] signal, int fs) {

        double[] weighted = new double[signal.length];
        for (int i = 0; i < signal.length; i++) {
            weighted[i] = signal[i] * 1.0;
        }
        return weighted;
    }
}