package com.example.onnx.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class OnnxConfig {

    private static final Logger log = LoggerFactory.getLogger(OnnxConfig.class);

    @Value("${onnx.model.path:src/main/resources/models/fraud_model.onnx}")
    private String modelPath;

    @Bean
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean
    public OrtSession ortSession(OrtEnvironment env) throws OrtException {
        Path path = Path.of(modelPath);
        if (!path.toFile().exists()) {
            throw new IllegalStateException(
                "ONNX model not found at: " + path.toAbsolutePath() +
                "\nRun: cd python-train-export && python train_export.py"
            );
        }

        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(2);

        OrtSession session = env.createSession(path.toString(), opts);
        log.info("ONNX model loaded from: {}", path.toAbsolutePath());
        session.getInputInfo().forEach((name, info) ->
            log.info("  Input  '{}': {}", name, info.getInfo()));
        session.getOutputInfo().forEach((name, info) ->
            log.info("  Output '{}': {}", name, info.getInfo()));
        return session;
    }
}
