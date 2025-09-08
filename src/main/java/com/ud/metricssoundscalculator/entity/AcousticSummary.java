package com.ud.metricssoundscalculator.entity;

import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class AcousticSummary {
    private int sampleRate;
    private int channels;
    private String weighting;
    private double leq;
    private Map<String, Double> ln;
    private double[] spectrumPreview;
    private Double deltaL;
    private Map<String, Double> octaveBands;
    private Map<String, Integer> levelHistogram;
}