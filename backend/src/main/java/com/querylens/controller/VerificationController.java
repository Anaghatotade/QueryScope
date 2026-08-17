package com.querylens.controller;

import com.querylens.service.VerificationResult;
import com.querylens.service.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<VerificationResult> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.verify(id));
    }
}
