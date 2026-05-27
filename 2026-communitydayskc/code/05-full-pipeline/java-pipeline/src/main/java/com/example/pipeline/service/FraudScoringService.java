package com.example.pipeline.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Hybrid fraud scorer: ONNX in-JVM (fast path) with REST ML service fallback.
 */
@Service
public class FraudScoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringService.class);

    @Value("${onnx.model.path:src/main/resources/models/fraud_model.onnx}")
    private String modelPath;

    @Value("${ml.service.url:http://ml-service:8000}")
    private String mlServiceUrl;

    private OrtEnvironment env;
    private OrtSession     session;
    private WebClient      mlClient;
    private boolean        onnxAvailable = false;

    @PostConstruct
    public void init() {
        env      = OrtEnvironment.getEnvironment();
        mlClient = WebClient.builder().baseUrl(mlServiceUrl).build();

        Path path = Path.of(modelPath);
        if (path.toFile().exists()) {
            try {
                session       = env.createSession(path.toString());
                onnxAvailable = true;
                log.info("ONNX model loaded — using in-JVM scoring");
            } catch (OrtException e) {
                log.warn("ONNX load failed — using REST fallback: {}", e.getMessage());
            }
        } else {
            log.info("ONNX model not found at {} — using REST fallback", path.toAbsolutePath());
        }
    }

    public double score(float[] features) {
        return onnxAvailable ? scoreWithOnnx(features) : scoreWithRest(features);
    }

    public boolean isOnnxAvailable() { return onnxAvailable; }

    private double scoreWithOnnx(float[] features) {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, new float[][]{features});
             OrtSession.Result result = session.run(Map.of("float_input", tensor))) {
            float[][] probs = (float[][]) result.get("probabilities").get().getValue();
            return probs[0][1];
        } catch (OrtException e) {
            log.error("ONNX inference failed, falling back to REST: {}", e.getMessage());
            return scoreWithRest(features);
        }
    }

    private double scoreWithRest(float[] features) {
        record Payload(float amount, float hour_of_day, float merchant_risk_score,
                       float velocity_30d, float geo_risk_score) {}
        record Response(double fraud_probability) {}
        try {
            Response resp = mlClient.post()
                .uri("/predict/fraud")
                .bodyValue(new Payload(features[0], features[1], features[2], features[3], features[4]))
                .retrieve()
                .bodyToMono(Response.class)
                .timeout(Duration.ofMillis(100))
                .block();
            return resp != null ? resp.fraud_probability() : 0.0;
        } catch (Exception e) {
            log.error("REST scoring failed — defaulting to 0.0: {}", e.getMessage());
            return 0.0;
        }
    }
}
