package com.querylens.controller;

import com.querylens.dto.AnalyzeRequest;
import com.querylens.service.AnalysisOrchestrator;
import com.querylens.service.AnalysisResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private final AnalysisOrchestrator orchestrator;

    public AnalysisController(AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        return ResponseEntity.ok(orchestrator.analyze(request.getSql()));
    }
}
