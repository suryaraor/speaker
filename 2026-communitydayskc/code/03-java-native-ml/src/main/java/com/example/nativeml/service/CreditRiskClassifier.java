package com.example.nativeml.service;

import com.example.nativeml.model.CreditDecision;
import com.example.nativeml.model.LoanApplication;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;
import weka.core.*;

import java.util.ArrayList;

/**
 * Trains and serves a credit-risk RandomForest classifier using Weka.
 * 100% Java — no Python, no ONNX, no external model files.
 */
@Service
public class CreditRiskClassifier {

    private static final Logger log = LoggerFactory.getLogger(CreditRiskClassifier.class);

    private RandomForest classifier;
    private Instances    dataStructure;

    @PostConstruct
    public void initialize() throws Exception {
        dataStructure = buildDataStructure();
        Instances trainingData = generateTrainingData(dataStructure);

        classifier = new RandomForest();
        classifier.setNumIterations(100);
        classifier.setSeed(42);
        classifier.buildClassifier(trainingData);

        log.info("Weka RandomForest trained on {} instances, {} attributes",
            trainingData.size(), trainingData.numAttributes() - 1);
    }

    public CreditDecision predict(LoanApplication app) throws Exception {
        Instance instance = toInstance(app, dataStructure);
        double[] distribution = classifier.distributionForInstance(instance);
        int classIdx = (int) classifier.classifyInstance(instance);
        String decision = dataStructure.classAttribute().value(classIdx);
        double lti = app.loanAmount() / Math.max(app.annualIncome(), 1);

        return new CreditDecision(
            decision,
            round(distribution[0]),
            round(distribution[1]),
            round(distribution[2]),
            round(lti),
            buildExplanation(app, decision, lti)
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static Instances buildDataStructure() {
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("credit_score"));
        attrs.add(new Attribute("annual_income"));
        attrs.add(new Attribute("loan_amount"));
        attrs.add(new Attribute("employment_years"));
        attrs.add(new Attribute("debt_to_income"));

        ArrayList<String> labels = new ArrayList<>();
        labels.add("APPROVE");
        labels.add("DENY");
        labels.add("REVIEW");
        attrs.add(new Attribute("decision", labels));

        Instances structure = new Instances("CreditRisk", attrs, 0);
        structure.setClassIndex(structure.numAttributes() - 1);
        return structure;
    }

    private static Instance toInstance(LoanApplication app, Instances structure) {
        double[] vals = new double[]{
            app.creditScore(),
            app.annualIncome(),
            app.loanAmount(),
            app.employmentYears(),
            app.debtToIncomeRatio(),
            Utils.missingValue()   // class is unknown — we're predicting it
        };
        Instance inst = new DenseInstance(1.0, vals);
        inst.setDataset(structure);
        return inst;
    }

    private static Instances generateTrainingData(Instances structure) {
        Instances data = new Instances(structure);
        add(data, 780, 95000, 20000, 8,  0.18, "APPROVE");
        add(data, 750, 75000, 15000, 5,  0.22, "APPROVE");
        add(data, 820, 120000, 40000, 12, 0.25, "APPROVE");
        add(data, 800, 110000, 35000, 10, 0.20, "APPROVE");
        add(data, 760, 85000, 22000, 7,  0.19, "APPROVE");
        add(data, 740, 70000, 18000, 6,  0.23, "APPROVE");
        add(data, 520, 35000, 30000, 1,  0.65, "DENY");
        add(data, 480, 28000, 25000, 0,  0.72, "DENY");
        add(data, 550, 40000, 35000, 2,  0.58, "DENY");
        add(data, 500, 32000, 28000, 1,  0.70, "DENY");
        add(data, 510, 30000, 27000, 0,  0.68, "DENY");
        add(data, 650, 55000, 25000, 3,  0.40, "REVIEW");
        add(data, 620, 48000, 20000, 4,  0.45, "REVIEW");
        add(data, 680, 60000, 30000, 5,  0.38, "REVIEW");
        add(data, 640, 52000, 22000, 3,  0.42, "REVIEW");
        return data;
    }

    private static void add(Instances data, double cs, double income,
                            double loan, double emp, double dti, String label) {
        double[] vals = new double[]{
            cs, income, loan, emp, dti,
            data.classAttribute().indexOfValue(label)
        };
        Instance inst = new DenseInstance(1.0, vals);
        inst.setDataset(data);
        data.add(inst);
    }

    private static String buildExplanation(LoanApplication app, String decision, double lti) {
        return switch (decision) {
            case "APPROVE" -> String.format(
                "Credit score %d is strong. DTI %.0f%% is within acceptable limits.",
                app.creditScore(), app.debtToIncomeRatio() * 100);
            case "DENY" -> String.format(
                "Credit score %d is below threshold or DTI %.0f%% is too high.",
                app.creditScore(), app.debtToIncomeRatio() * 100);
            default -> String.format(
                "Borderline case — loan-to-income %.2f requires manual review.", lti);
        };
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
