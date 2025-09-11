package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpectrogramDTO {
    private double[][] spectrogram; // Matriz tiempo-frecuencia
    private int sampleRate;         // Frecuencia de muestreo
    private int channels;           // Número de canales
    private int windowSizeSec;      // Ventana usada en segundos
    private int frames;             // Número de ventanas calculadas
}