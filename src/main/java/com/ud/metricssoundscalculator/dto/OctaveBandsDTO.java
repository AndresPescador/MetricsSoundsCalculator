package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class OctaveBandsDTO {
    private Map<String, Double> octaveBands; // Energía en cada banda
    private int sampleRate;                  // Frecuencia de muestreo
    private int channels;                    // Número de canales
}