package com.ud.metricssoundscalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ud.metricssoundscalculator.dto.*;

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

    // Retorna todo el análisis
    public AcousticAnalysisDTO getAnalysis(File wavFile, int windowSec) throws Exception {
        AudioData audioData = loadAudioData(wavFile);

        // --- Estadísticas básicas ---
        double leq = calculateLeq(audioData.getSignal());
        Map<String, Double> ln = calculateLn(audioData.getSignal(), audioData.getSampleRate());
        double[] lmaxmin = computeLmaxLmin(audioData.getSignal());
        double deltaL = computeDeltaL(ln);
        double durationAbove65 = computeDurationAbove(audioData.getSignal(), audioData.getSampleRate(), 65);
        double durationAbove70 = computeDurationAbove(audioData.getSignal(), audioData.getSampleRate(), 70);

        // --- Series temporales ---
        double[] levels = computeFrameLevels(audioData.getSignal(), audioData.getSampleRate(), 125);
        double[] leqSeries = computeLeqMoving(audioData.getSignal(), audioData.getSampleRate(), windowSec);

        // --- Frecuencia ---
        double[] spectrum = correctionService.computeSpectrum(audioData.getSignal(), audioData.getSampleRate());
        Map<String, Double> octaveBands = computeOctaveBands(spectrum, audioData.getSampleRate());
        double[][] spectrogram = computeSpectrogram(audioData.getSignal(), audioData.getSampleRate(), windowSec);

        // --- Histogramas ---
        Map<String, Integer> histogram = computeLevelHistogram(levels);

        // --- Construcción DTO completo ---
        AcousticAnalysisDTO dto = new AcousticAnalysisDTO();
        dto.setSampleRate(audioData.getSampleRate());
        dto.setChannels(audioData.getChannels());
        dto.setWeighting("A"); // fijo porque aplicamos ponderación A

        dto.setLeq(leq);
        dto.setLn(ln);
        dto.setLmax(lmaxmin[0]);
        dto.setLmin(lmaxmin[1]);
        dto.setDeltaL(deltaL);

        dto.setDurationAbove65(durationAbove65);
        dto.setDurationAbove70(durationAbove70);

        dto.setLevels(levels);
        dto.setLeqSeries(leqSeries);

        dto.setSpectrumPreview(Arrays.copyOf(spectrum, Math.min(512, spectrum.length)));
        dto.setOctaveBands(octaveBands);
        dto.setSpectrogram(spectrogram);

        dto.setLevelHistogram(histogram);

        // Espaciales (placeholder hasta que se implementen)
        dto.setIacc(null);
        dto.setTiacc(null);
        dto.setWiacc(null);

        saveResultToFile(dto, wavFile.getName());

        return dto;
    }

    // Retorna DTO con histograma y datos base
    public HistogramDTO getHistogram(File wavFile) throws Exception {
        AudioData audioData = loadAudioData(wavFile);

        double[] frameLevels = computeFrameLevels(audioData.getSignal(), audioData.getSampleRate(), 125);
        Map<String, Integer> histogram = computeLevelHistogram(frameLevels);
        double[] lmaxmin = computeLmaxLmin(audioData.getSignal());
        double leq = calculateLeq(audioData.getSignal());

        HistogramDTO dto = new HistogramDTO();
        dto.setHistogram(histogram);
        dto.setLmin(lmaxmin[1]);
        dto.setLmax(lmaxmin[0]);
        dto.setLeq(leq);
        dto.setSampleRate(audioData.getSampleRate());
        dto.setChannels(audioData.getChannels());
        return dto;
    }

    // Retorna DTO con espectrograma
    public SpectrogramDTO getSpectrogram(File wavFile, int windowSec) throws Exception {
        AudioData audioData = loadAudioData(wavFile);

        double[][] spectrogram = computeSpectrogram(audioData.getSignal(), audioData.getSampleRate(), windowSec);

        SpectrogramDTO dto = new SpectrogramDTO();
        dto.setSpectrogram(spectrogram);
        dto.setSampleRate(audioData.getSampleRate());
        dto.setWindowSizeSec(windowSec);
        dto.setFrames(spectrogram.length);
        dto.setChannels(audioData.getChannels());
        return dto;
    }

    // Retorna DTO con evolución temporal de Leq
    public LeqSeriesDTO getLeqSeries(File wavFile, int windowSec) throws Exception {
        AudioData audioData = loadAudioData(wavFile);

        double[] series = computeLeqMoving(audioData.getSignal(), audioData.getSampleRate(), windowSec);

        LeqSeriesDTO dto = new LeqSeriesDTO();
        dto.setLeqSeries(series);
        dto.setWindowSizeSec(windowSec);
        dto.setSampleRate(audioData.getSampleRate());
        dto.setChannels(audioData.getChannels());
        return dto;
    }

    // Retorna DTO con bandas de octava
    public OctaveBandsDTO getOctaveBands(File wavFile) throws Exception {
        AudioData audioData = loadAudioData(wavFile);

        double[] spectrum = correctionService.computeSpectrum(audioData.getSignal(), audioData.getSampleRate());
        Map<String, Double> bands = computeOctaveBands(spectrum, audioData.getSampleRate());

        OctaveBandsDTO dto = new OctaveBandsDTO();
        dto.setOctaveBands(bands);
        dto.setSampleRate(audioData.getSampleRate());
        dto.setChannels(audioData.getChannels());
        return dto;
    }

    private AudioData loadAudioData(File wavFile) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = audioStream.getFormat();

        int fs = (int) format.getSampleRate();
        int channels = format.getChannels();

        byte[] audioBytes = audioStream.readAllBytes();
        double[] signal = correctionService.bytesToDoubleArray(audioBytes, format);

        // Aplicar ponderación A por defecto (siempre trabajamos en dBA)
        double[] weightedSignal = weightingService.applyAWeighting(signal, fs);

        AudioData audioData = new AudioData();
        audioData.setSignal(weightedSignal);
        audioData.setSampleRate(fs);
        audioData.setChannels(channels);

        return audioData;
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

    private double[] computeLmaxLmin(double[] signal) {
        double lmax = Double.NEGATIVE_INFINITY;
        double lmin = Double.POSITIVE_INFINITY;

        for (double sample : signal) {
            double db = 20 * Math.log10(Math.abs(sample) + 1e-12);
            if (db > lmax) lmax = db;
            if (db < lmin) lmin = db;
        }

        return new double[]{lmax, lmin};
    }

    private double computeDurationAbove(double[] signal, int fs, double thresholdDb) {
        int frameSize = (int)(0.125 * fs); // 125 ms
        int frames = signal.length / frameSize;

        int countAbove = 0;
        for (int f = 0; f < frames; f++) {
            double sumSq = 0;
            for (int i = 0; i < frameSize; i++) {
                int idx = f * frameSize + i;
                if (idx < signal.length) {
                    sumSq += signal[idx] * signal[idx];
                }
            }
            double rms = Math.sqrt(sumSq / frameSize);
            double levelDb = 20 * Math.log10(rms + 1e-12);

            if (levelDb > thresholdDb) {
                countAbove++;
            }
        }

        double durationSec = (countAbove * frameSize) / (double) fs;
        return durationSec;
    }

    private double[] computeLeqMoving(double[] signal, int fs, int windowSec) {
        int frameSize = fs * windowSec;
        int frames = signal.length / frameSize;

        double[] leqSeries = new double[frames];
        for (int f = 0; f < frames; f++) {
            double sumSq = 0;
            for (int i = 0; i < frameSize; i++) {
                int idx = f * frameSize + i;
                if (idx < signal.length) {
                    sumSq += signal[idx] * signal[idx];
                }
            }
            double rms = Math.sqrt(sumSq / frameSize);
            leqSeries[f] = 20 * Math.log10(rms + 1e-12);
        }

        return leqSeries;
    }

    private double[][] computeSpectrogram(double[] signal, int fs, int windowSec) {
        int windowSize = fs * windowSec;
        int frames = signal.length / windowSize;

        double[][] spectrogram = new double[frames][];
        for (int f = 0; f < frames; f++) {
            int start = f * windowSize;
            int end = Math.min(start + windowSize, signal.length);
            double[] segment = Arrays.copyOfRange(signal, start, end);

            double[] spectrum = correctionService.computeSpectrum(segment, fs);
            spectrogram[f] = spectrum;
        }

        return spectrogram;
    }

    private void saveResultToFile(AcousticAnalysisDTO dto, String baseName) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto);

        Path dir = Paths.get("results");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve(baseName.replace(".wav", "_result.txt"));
        Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}