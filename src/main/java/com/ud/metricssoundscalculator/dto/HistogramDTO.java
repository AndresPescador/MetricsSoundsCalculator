package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class HistogramDTO {
    private Map<String, Integer> histogram; // Niveles de dB -> frecuencia de aparición
    private double lmin;    // Nivel mínimo
    private double lmax;    // Nivel máximo
    private double leq;     // Nivel equivalente
    private int sampleRate; // Frecuencia de muestreo
    private int channels;   // Número de canales
}