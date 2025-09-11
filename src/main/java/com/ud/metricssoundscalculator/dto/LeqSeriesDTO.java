package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeqSeriesDTO {
    private double[] leqSeries; // Serie temporal del Leq (ventanas sucesivas)
    private int windowSizeSec;  // Tamaño de la ventana en segundos
    private int sampleRate;     // Frecuencia de muestreo
    private int channels;       // Número de canales
}