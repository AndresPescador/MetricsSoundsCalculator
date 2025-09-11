package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AudioData {
    private double[] signal;   // señal en double[]
    private int sampleRate;    // frecuencia de muestreo
    private int channels;      // número de canales
}