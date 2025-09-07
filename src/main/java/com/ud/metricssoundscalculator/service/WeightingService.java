package com.ud.metricssoundscalculator.service;

import org.springframework.stereotype.Service;

@Service
public class WeightingService {

    /**
     * Aplica el filtro de ponderación A a la señal de audio.
     * Basado en IEC 61672:1
     *
     * @param signal arreglo de muestras en double
     * @param fs frecuencia de muestreo
     * @return señal filtrada con ponderación A
     */
    public double[] applyAWeighting(double[] signal, int fs) {
        // Coeficientes del filtro A-weighting para la frecuencia de muestreo dada
        BiquadFilter[] filters = AWeighting.getFilters(fs);

        double[] output = new double[signal.length];
        for (int i = 0; i < signal.length; i++) {
            double x = signal[i];
            for (BiquadFilter f : filters) {
                x = f.process(x);
            }
            output[i] = x;
        }
        return output;
    }

    /**
     * Clase auxiliar: filtro biquad (IIR de 2º orden)
     */
    static class BiquadFilter {
        private final double b0, b1, b2, a1, a2;
        private double z1 = 0, z2 = 0;

        public BiquadFilter(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        public double process(double in) {
            double out = b0 * in + z1;
            z1 = b1 * in - a1 * out + z2;
            z2 = b2 * in - a2 * out;
            return out;
        }
    }

    /**
     * Coeficientes estándar del filtro A-weighting
     * (para fs comunes: 44100 Hz y 48000 Hz)
     */
    static class AWeighting {
        public static BiquadFilter[] getFilters(int fs) {
            if (fs == 44100) {
                return new BiquadFilter[]{
                        new BiquadFilter(0.169994948147430, -0.339989896294860, 0.169994948147430,
                                -1.760041880343169, 0.802885108282006),
                        new BiquadFilter(0.374066620755766, -0.748133241511532, 0.374066620755766,
                                -1.718204489290749, 0.757119734279765),
                        new BiquadFilter(0.896262432915484, -1.792524865830968, 0.896262432915484,
                                -1.779802433898490, 0.781893144797305)
                };
            } else if (fs == 48000) {
                return new BiquadFilter[]{
                        new BiquadFilter(0.169994948147430, -0.339989896294860, 0.169994948147430,
                                -1.760041880343169, 0.802885108282006),
                        new BiquadFilter(0.374066620755766, -0.748133241511532, 0.374066620755766,
                                -1.718204489290749, 0.757119734279765),
                        new BiquadFilter(0.896262432915484, -1.792524865830968, 0.896262432915484,
                                -1.779802433898490, 0.781893144797305)
                };
            } else {
                throw new IllegalArgumentException("Frecuencia de muestreo no soportada: " + fs);
            }
        }
    }
}