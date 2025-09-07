package com.ud.metricssoundscalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ud.metricssoundscalculator.entity.AcousticResult;
import com.ud.metricssoundscalculator.entity.AcousticSummary;

import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Service
public class AcousticService {

    private final SpatialService spatialService = new SpatialService();
    private final WeightingService weightingService = new WeightingService();
    private final CorrectionService correctionService = new CorrectionService();

    public AcousticSummary computeParameters(File wavFile) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = audioStream.getFormat();

        int fs = (int) format.getSampleRate();
        int channels = format.getChannels();

        byte[] audioBytes = audioStream.readAllBytes();
        double[] signal = correctionService.bytesToDoubleArray(audioBytes, format);

        // Aplicar ponderación A
        double[] weightedSignal = weightingService.applyAWeighting(signal, fs);

        // Calcular métricas
        double leq = calculateLeq(weightedSignal);
        Map<String, Double> ln = calculateLn(weightedSignal, fs);
        double[] levels = correctionService.computeSpectrum(weightedSignal, fs);

        Map<String, Object> spatial = null;
        if (channels == 2) {
            spatial = spatialService.computeSpatialParams(wavFile);
        }

        // Construir resultado completo
        AcousticResult result = new AcousticResult();
        result.setSampleRate(fs);
        result.setChannels(channels);
        result.setWeighting("A");
        result.setLeq(leq);
        result.setLevels(levels);
        result.setLn(ln);
        if (spatial != null) {
            result.setIacc((Double) spatial.get("IACC"));
            result.setTiacc((List<Double>) spatial.get("TIACC"));
            result.setWiacc((Double) spatial.get("WIACC"));
        }

        // Guardar resultado completo en un archivo txt (JSON dentro)
        saveResultToFile(result, wavFile.getName());

        // Construir resumen
        AcousticSummary summary = new AcousticSummary();
        summary.setSampleRate(fs);
        summary.setChannels(channels);
        summary.setWeighting("A");
        summary.setLeq(leq);
        summary.setLn(ln);

        // Resumir levels → tomar, por ejemplo, cada 50º valor
        int step = Math.max(1, levels.length / 100);
        int previewSize = (int) Math.ceil((double) levels.length / step);

        double[] preview = new double[previewSize];
        for (int i = 0, j = 0; i < levels.length && j < previewSize; i += step, j++) {
            preview[j] = levels[i];
        }
        summary.setSpectrumPreview(preview);

        return summary;
    }

    private void saveResultToFile(AcousticResult result, String baseName) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

        Path dir = Paths.get("results");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve(baseName.replace(".wav", "_result.txt"));
        Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private double calculateLeq(double[] signal) {
        double sumSq = 0;
        for (double s : signal) sumSq += s * s;
        double rms = Math.sqrt(sumSq / signal.length);
        return 20 * Math.log10(rms / 20e-6 + 1e-9);
    }

    private Map<String, Double> calculateLn(double[] signal, int fs) {
        int frameLen = fs; // 1 segundo
        int frames = signal.length / frameLen;
        double[] leqFrames = new double[frames];

        for (int i = 0; i < frames; i++) {
            double[] frame = Arrays.copyOfRange(signal, i * frameLen, (i + 1) * frameLen);
            leqFrames[i] = calculateLeq(frame);
        }

        Arrays.sort(leqFrames);
        Map<String, Double> ln = new HashMap<>();
        ln.put("L10", percentile(leqFrames, 90));
        ln.put("L50", percentile(leqFrames, 50));
        ln.put("L90", percentile(leqFrames, 10));
        return ln;
    }

    private double percentile(double[] arr, double p) {
        int idx = (int) Math.ceil(p / 100.0 * arr.length) - 1;
        return arr[Math.min(idx, arr.length - 1)];
    }
}