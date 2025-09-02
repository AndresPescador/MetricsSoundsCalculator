package com.ud.metricssoundscalculator.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AcousticResult {
    private int sampleRate;
    private int channels;
    private String weighting;
    private double leq;
    private double[] levels;
    private Map<String, Double> ln;

    private Double iacc;
    private List<Double> tiacc;
    private Double wiacc;
}