package com.ud.metricssoundscalculator.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AcousticAnalysisDTO {
    private int sampleRate;          // Frecuencia de muestreo
    private int channels;            // Número de canales
    private String weighting;        // Tipo de ponderación (A, C, Z)

    // Estadísticas básicas
    private double leq;              // Nivel equivalente global
    private Map<String, Double> ln;  // Percentiles L10, L50, L90
    private double lmax;             // Nivel máximo
    private double lmin;             // Nivel mínimo

    // Duraciones sobre umbrales normativos
    private double durationAbove65;
    private double durationAbove70;

    // Variabilidad
    private double deltaL;           // L10 - L90

    // Series temporales
    private double[] levels;         // Niveles por frames cortos (~125 ms)
    private double[] leqSeries;      // Evolución temporal de Leq (ej. ventanas de 1 min)

    // Representaciones en frecuencia
    private double[] spectrumPreview;       // Espectro reducido para graficar
    private Map<String, Double> octaveBands; // Bandas de tercio de octava
    private double[][] spectrogram;         // Espectrograma tiempo-frecuencia

    // Histogramas
    private Map<String, Integer> levelHistogram; // Distribución de niveles en rangos dB

    // Parámetros espaciales (si es estéreo)
    private Double iacc;              // Coherencia interaural
    private List<Double> tiacc;       // IACC en el tiempo
    private Double wiacc;             // IACC ponderado
}