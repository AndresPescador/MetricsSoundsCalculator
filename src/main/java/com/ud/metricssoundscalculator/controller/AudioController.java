package com.ud.metricssoundscalculator.controller;

import com.ud.metricssoundscalculator.entity.AcousticResult;
import com.ud.metricssoundscalculator.service.AcousticService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@RestController
@RequestMapping("/audio")
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
public ResponseEntity<AcousticResult> analyzeAudio(
        @RequestParam("file") MultipartFile file) {
    try {
        File tempFile = File.createTempFile("upload_", ".wav");
        file.transferTo(tempFile);

        AcousticResult result = acousticService.computeParameters(tempFile);

        tempFile.delete();
        return ResponseEntity.ok(result);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError().build();
    }
}

}
