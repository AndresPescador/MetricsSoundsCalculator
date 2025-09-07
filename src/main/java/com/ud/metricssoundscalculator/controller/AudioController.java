package com.ud.metricssoundscalculator.controller;

import com.ud.metricssoundscalculator.entity.AcousticResult;
import com.ud.metricssoundscalculator.entity.AcousticSummary;
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

    @PostMapping(
    value = "/analyze",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<AcousticSummary> analyzeAudio(
        @RequestParam("file") MultipartFile file) {
    try {

        File tempFile = File.createTempFile("upload_", ".wav");
        file.transferTo(tempFile);

        AcousticSummary summary = acousticService.computeParameters(tempFile);

        tempFile.delete();

        return ResponseEntity.ok(summary);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError().build();
    }
}

}
