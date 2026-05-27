package com.example.nativeml.controller;

import com.example.nativeml.model.CreditDecision;
import com.example.nativeml.model.LoanApplication;
import com.example.nativeml.service.CreditRiskClassifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credit")
public class CreditRiskController {

    private final CreditRiskClassifier classifier;

    public CreditRiskController(CreditRiskClassifier classifier) {
        this.classifier = classifier;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<CreditDecision> evaluate(@RequestBody LoanApplication app)
            throws Exception {
        return ResponseEntity.ok(classifier.predict(app));
    }

    @GetMapping("/demo/approve")
    public ResponseEntity<CreditDecision> demoApprove() throws Exception {
        return evaluate(new LoanApplication("APP-001", 775, 90000, 20000, 7, 0.20));
    }

    @GetMapping("/demo/deny")
    public ResponseEntity<CreditDecision> demoDeny() throws Exception {
        return evaluate(new LoanApplication("APP-002", 510, 32000, 28000, 0, 0.70));
    }

    @GetMapping("/demo/review")
    public ResponseEntity<CreditDecision> demoReview() throws Exception {
        return evaluate(new LoanApplication("APP-003", 645, 54000, 24000, 3, 0.41));
    }
}
