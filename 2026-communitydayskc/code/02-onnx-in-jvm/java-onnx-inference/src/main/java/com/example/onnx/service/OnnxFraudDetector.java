package com.example.onnx.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.onnx.model.FraudPrediction;
import com.example.onnx.model.TransactionFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Runs fraud detection inference entirely inside the JVM using ONNX Runtime.
 * No Python process, no HTTP call, no network latency. Typically < 1ms per transaction.
 */
@Service
public class OnnxFraudDetector {

    private static final Logger log = LoggerFactory.getLogger(OnnxFraudDetector.class);

    private final OrtEnvironment env;
    private final OrtSession    session;

    public OnnxFraudDetector(OrtEnvironment env, OrtSession session) {
        this.env     = env;
        this.session = session;
    }

    public FraudPrediction predict(TransactionFeatures features) throws OrtException {
        long start = System.nanoTime();

        float[][] inputMatrix = new float[][]{features.toArray()};

        try (OnnxTensor tensor = OnnxTensor.createTensor(env, inputMatrix);
             OrtSession.Result result = session.run(Map.of("float_input", tensor))) {

            // result.get(name) returns Optional<OnnxValue> — call .get() to unwrap
            float[][] probs = (float[][]) result.get("probabilities").get().getValue();
            float fraudProb = probs[0][1];

            long elapsedNs = System.nanoTime() - start;
            log.debug("ONNX inference: {}ms, fraud_prob={}", elapsedNs / 1_000_000.0, fraudProb);

            return new FraudPrediction(fraudProb, fraudProb > 0.85f, riskLevel(fraudProb), elapsedNs);
        }
    }

    private static String riskLevel(float prob) {
        if (prob > 0.75f) return "HIGH";
        if (prob > 0.40f) return "MEDIUM";
        return "LOW";
    }
}
