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

        // Calcular métricas principales
        double leq = calculateLeq(weightedSignal);
        Map<String, Double> ln = calculateLn(weightedSignal, fs);

        // Niveles por ventana de 125 ms (para histograma y variabilidad)
        double[] frameLevels = computeFrameLevels(weightedSignal, fs, 125);

        // Espectro de la señal completa
        double[] spectrum = correctionService.computeSpectrum(weightedSignal, fs);

        // Parámetros espaciales si es estéreo
        Map<String, Object> spatial = null;
        if (channels == 2) {
            spatial = spatialService.computeSpatialParams(wavFile);
        }

        // Variabilidad
        double deltaL = computeDeltaL(ln);

        // Bandas de octava
        Map<String, Double> octaveBands = computeOctaveBands(spectrum, fs);

        // Histograma de niveles (usando frameLevels en dB)
        Map<String, Integer> histogram = computeLevelHistogram(frameLevels);

        // Construir resultado completo
        AcousticResult result = new AcousticResult();
        result.setSampleRate(fs);
        result.setChannels(channels);
        result.setWeighting("A");
        result.setLeq(leq);
        result.setLevels(frameLevels);
        result.setLn(ln);
        result.setIacc(spatial != null ? (Double) spatial.get("IACC") : null);
        result.setTiacc(spatial != null ? (List<Double>) spatial.get("TIACC") : null);
        result.setWiacc(spatial != null ? (Double) spatial.get("WIACC") : null);

        // Guardar resultado en archivo
        saveResultToFile(result, wavFile.getName());

        // Construir resumen
        AcousticSummary summary = new AcousticSummary();
        summary.setSampleRate(fs);
        summary.setChannels(channels);
        summary.setWeighting("A");
        summary.setLeq(leq);
        summary.setLn(ln);

        // Resumir espectro (preview de 100 puntos máx)
        int step = Math.max(1, spectrum.length / 100);
        int previewSize = (int) Math.ceil((double) spectrum.length / step);
        double[] preview = new double[previewSize];
        for (int i = 0, j = 0; i < spectrum.length && j < previewSize; i += step, j++) {
            preview[j] = spectrum[i];
        }
        summary.setSpectrumPreview(preview);

        // Añadir estadísticas nuevas
        summary.setDeltaL(deltaL);
        summary.setOctaveBands(octaveBands);
        summary.setLevelHistogram(histogram);

        return summary;
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

    /**
     * Calcula bandas de tercio de octava (simplificado)
     */
    public Map<String, Double> computeOctaveBands(double[] spectrum, int sampleRate) {
        Map<String, Double> bands = new LinkedHashMap<>();

        // Frecuencias centrales normalizadas ISO 266 (31.5 Hz – 16 kHz)
        double[] centers = {31.5, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

        double binWidth = (double) sampleRate / (2 * spectrum.length);

        for (double fc : centers) {
            double fLow = fc / Math.pow(2, 1.0/6);   // -1/6 de octava
            double fHigh = fc * Math.pow(2, 1.0/6); // +1/6 de octava

            int binLow = (int) Math.floor(fLow / binWidth);
            int binHigh = (int) Math.ceil(fHigh / binWidth);

            double sum = 0.0;
            for (int i = binLow; i < binHigh && i < spectrum.length; i++) {
                sum += Math.pow(spectrum[i], 2); // potencia
            }

            double levelDb = 10 * Math.log10(sum + 1e-12);
            bands.put(String.format("%.0f Hz", fc), levelDb);
        }

        return bands;
    }

    /**
     * Histograma de niveles (intervalos de 5 dB entre 30–100 dB)
     */
    public Map<String, Integer> computeLevelHistogram(double[] levels) {
        Map<String, Integer> histogram = new LinkedHashMap<>();

        int min = 30;
        int max = 100;
        int step = 5;

        for (int db = min; db < max; db += step) {
            String key = db + "–" + (db + step) + " dB";
            histogram.put(key, 0);
        }

        for (double l : levels) {
            int bin = ((int) l - min) / step;
            if (bin >= 0 && bin < histogram.size()) {
                String key = (min + bin*step) + "–" + (min + (bin+1)*step) + " dB";
                histogram.put(key, histogram.get(key) + 1);
            }
        }

        return histogram;
    }

    /**
     * Calcula ΔL = L10 - L90
     */
    private double computeDeltaL(Map<String, Double> ln) {
        Double l10 = ln.get("L10");
        Double l90 = ln.get("L90");
        if (l10 != null && l90 != null) {
            return l10 - l90;
        }
        return Double.NaN;
    }

    /**
     * Calcula niveles sonoros por ventana de tiempo (en dB SPL)
     */
    public double[] computeFrameLevels(double[] signal, int fs, int windowMs) {
        int windowSize = (int) (fs * (windowMs / 1000.0));
        int numFrames = signal.length / windowSize;
        double[] levels = new double[numFrames];

        for (int i = 0; i < numFrames; i++) {
            double sumSq = 0.0;
            for (int j = 0; j < windowSize; j++) {
                double sample = signal[i * windowSize + j];
                sumSq += sample * sample;
            }
            double meanSq = sumSq / windowSize;
            levels[i] = 10 * Math.log10(meanSq + 1e-12);
        }

        return levels;
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
}