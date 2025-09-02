package com.ud.metricssoundscalculator.service;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.*;

@Service
public class SpatialService {

    public Map<String, Object> computeSpatialParams(File wavFile) throws Exception {
        Map<String, Object> results = new HashMap<>();

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = audioStream.getFormat();

        int sampleRate = (int) format.getSampleRate();
        int channels = format.getChannels();

        if (channels < 2) {
            throw new IllegalArgumentException("Se necesita audio estéreo para calcular IACC.");
        }

        int windowSize = sampleRate * 2;  // 2 s
        int overlap = sampleRate * 2 - (int)(0.1 * sampleRate);

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(wavFile, windowSize, overlap);

        List<Double> tiacc = new ArrayList<>();

        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                int n = buffer.length / 2;

                double[] left = new double[n];
                double[] right = new double[n];
                for (int i = 0; i < n; i++) {
                    left[i] = buffer[2 * i];
                    right[i] = buffer[2 * i + 1];
                }

                double iaccVal = computeIACC(left, right, sampleRate);
                tiacc.add(iaccVal);

                return true;
            }

            @Override
            public void processingFinished() { }
        });

        dispatcher.run();

        double wiacc = tiacc.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        results.put("IACC", Collections.max(tiacc));
        results.put("TIACC", tiacc);
        results.put("WIACC", wiacc);

        return results;
    }

    private double computeIACC(double[] left, double[] right, int sampleRate) {
        int maxLag = sampleRate / 1000;
        double maxCorr = 0;

        double normL = 0, normR = 0;
        for (int i = 0; i < left.length; i++) {
            normL += left[i] * left[i];
            normR += right[i] * right[i];
        }
        normL = Math.sqrt(normL);
        normR = Math.sqrt(normR);

        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double sum = 0;
            for (int i = 0; i < left.length; i++) {
                int j = i + lag;
                if (j >= 0 && j < right.length) {
                    sum += left[i] * right[j];
                }
            }
            double corr = Math.abs(sum / (normL * normR + 1e-9));
            if (corr > maxCorr) {
                maxCorr = corr;
            }
        }
        return maxCorr;
    }
}