package com.example.onnx.controller;

import ai.onnxruntime.OrtException;
import com.example.onnx.model.FraudPrediction;
import com.example.onnx.model.TransactionFeatures;
import com.example.onnx.service.OnnxFraudDetector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/infer")
public class InferenceController {

    private final OnnxFraudDetector detector;

    public InferenceController(OnnxFraudDetector detector) {
        this.detector = detector;
    }

    @PostMapping("/fraud")
    public ResponseEntity<FraudPrediction> infer(@RequestBody TransactionFeatures features)
            throws OrtException {
        return ResponseEntity.ok(detector.predict(features));
    }

    @GetMapping("/demo/high-risk")
    public ResponseEntity<FraudPrediction> demoHighRisk() throws OrtException {
        return infer(new TransactionFeatures(1800f, 3f, 0.90f, 28f, 0.82f));
    }

    @GetMapping("/demo/low-risk")
    public ResponseEntity<FraudPrediction> demoLowRisk() throws OrtException {
        return infer(new TransactionFeatures(42f, 14f, 0.03f, 4f, 0.10f));
    }

    @GetMapping("/benchmark")
    public ResponseEntity<Map<String, Object>> benchmark() throws OrtException {
        TransactionFeatures sample = new TransactionFeatures(500f, 10f, 0.30f, 8f, 0.20f);
        int iterations = 1000;
        long start = System.nanoTime();
        FraudPrediction last = null;
        for (int i = 0; i < iterations; i++) {
            last = detector.predict(sample);
        }
        long totalNs = System.nanoTime() - start;
        return ResponseEntity.ok(Map.of(
            "iterations", iterations,
            "total_ms", totalNs / 1_000_000.0,
            "avg_ms_per_inference", totalNs / 1_000_000.0 / iterations,
            "last_prediction", last
        ));
    }
}
