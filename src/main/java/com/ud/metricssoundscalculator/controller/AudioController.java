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

    @PostMapping("/analyze")
    public AcousticResult analyzeAudio(@RequestParam("file") MultipartFile file) throws Exception {
        File tempFile = File.createTempFile("upload", ".wav");
        file.transferTo(tempFile);
        return acousticService.computeParameters(tempFile);
    }
}