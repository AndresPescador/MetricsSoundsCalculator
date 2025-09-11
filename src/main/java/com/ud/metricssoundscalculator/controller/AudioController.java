package com.ud.metricssoundscalculator.controller;

import com.ud.metricssoundscalculator.dto.*;

import com.ud.metricssoundscalculator.service.AcousticService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.io.File;

@RestController
@RequestMapping("/audio")
@CrossOrigin(origins = "*")
public class AudioController {

    private final AcousticService acousticService;

    public AudioController(AcousticService acousticService) {
        this.acousticService = acousticService;
    }

    // 0. Todas las estadísticas
    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AcousticAnalysisDTO> analyzeAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec
    ) {
        try {
            // Crear archivo temporal
            File tempFile = File.createTempFile("upload_", ".wav");
            file.transferTo(tempFile);

            // Usamos el método unificado
            AcousticAnalysisDTO analysis = acousticService.getAnalysis(tempFile, windowSec);

            // Borrar archivo temporal
            tempFile.delete();

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 1. Histograma
    @PostMapping(
            value = "/histogram",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<HistogramDTO> getHistogram(
            @RequestParam("file") MultipartFile file) {
        try {
            File tempFile = File.createTempFile("upload_", ".wav");
            file.transferTo(tempFile);

            HistogramDTO dto = acousticService.getHistogram(tempFile);

            tempFile.delete();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 2. Espectrograma
    @PostMapping(
            value = "/spectrogram",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SpectrogramDTO> getSpectrogram(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "1") int windowSec) {
        try {
            File tempFile = File.createTempFile("upload_", ".wav");
            file.transferTo(tempFile);

            SpectrogramDTO dto = acousticService.getSpectrogram(tempFile, windowSec);

            tempFile.delete();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 3. Serie temporal de Leq
    @PostMapping(
            value = "/leq-series",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LeqSeriesDTO> getLeqSeries(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "60") int windowSec) {
        try {
            File tempFile = File.createTempFile("upload_", ".wav");
            file.transferTo(tempFile);

            LeqSeriesDTO dto = acousticService.getLeqSeries(tempFile, windowSec);

            tempFile.delete();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 4. Bandas de octava
    @PostMapping(
            value = "/octave-bands",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OctaveBandsDTO> getOctaveBands(
            @RequestParam("file") MultipartFile file) {
        try {
            File tempFile = File.createTempFile("upload_", ".wav");
            file.transferTo(tempFile);

            OctaveBandsDTO dto = acousticService.getOctaveBands(tempFile);

            tempFile.delete();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }


}
