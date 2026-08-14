package com.example.smartcanteen.domain;

import java.util.List;

public record RiskAssessment(int score, String level, List<String> factors) {

    public RiskAssessment {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
