package com.ud.metricssoundscalculator.service;

import be.tarsos.dsp.util.fft.FFT;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;

@Service
public class CorrectionService {

    public double[] bytesToDoubleArray(byte[] bytes, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        int length = bytes.length / sampleSize;
        double[] samples = new double[length];

        for (int i = 0; i < length; i++) {
            int value = 0;
            for (int b = 0; b < sampleSize; b++) {
                value |= (bytes[i * sampleSize + b] & 0xFF) << (b * 8);
            }
            samples[i] = value / Math.pow(2, format.getSampleSizeInBits() - 1);
        }
        return samples;
    }

    public double[] computeSpectrum(double[] samples, int sampleRate) {
        int n = samples.length;

        // FFT en TarsosDSP requiere float[]
        float[] floatSamples = new float[n];
        for (int i = 0; i < n; i++) {
            floatSamples[i] = (float) samples[i];
        }

        // FFT necesita tamaño potencia de 2 → padding si no lo es
        int fftSize = 1;
        while (fftSize < n) {
            fftSize *= 2;
        }

        FFT fft = new FFT(fftSize);
        float[] fftData = new float[fftSize * 2]; // real + imag

        // Copiamos los datos
        System.arraycopy(floatSamples, 0, fftData, 0, n);

        // Ejecutamos FFT
        fft.forwardTransform(fftData);

        // Magnitudes
        float[] spectrum = new float[fftSize / 2];
        fft.modulus(fftData, spectrum);

        // Convertimos a double para el resto de cálculos
        double[] spectrumDouble = new double[spectrum.length];
        for (int i = 0; i < spectrum.length; i++) {
            spectrumDouble[i] = spectrum[i];
        }

        return spectrumDouble;
    }

}