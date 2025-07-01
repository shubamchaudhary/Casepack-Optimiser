package com.casepackoptimizer.controller;

import com.casepackoptimizer.dto.CasepackOptimizerRequest;
import com.casepackoptimizer.dto.CasepackOptimizerResponse;
import com.casepackoptimizer.service.CasepackOptimizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/casepack")
@RequiredArgsConstructor
public class CasepackOptimizerController {

    private final CasepackOptimizerService casepackOptimizerService;

    @PostMapping("/optimize")
    public ResponseEntity<CasepackOptimizerResponse> optimizeCasepacks(@RequestBody CasepackOptimizerRequest request) {
        CasepackOptimizerResponse response = casepackOptimizerService.optimizeCasepacks(request);
        return ResponseEntity.ok(response);
    }
}