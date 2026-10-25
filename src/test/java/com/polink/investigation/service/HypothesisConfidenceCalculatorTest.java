package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// DB·AI 호출 없이 서버의 자료 부합도 계산식만 검사
class HypothesisConfidenceCalculatorTest {

    private final HypothesisConfidenceCalculator calculator =
            new HypothesisConfidenceCalculator();

    @Test
    void noSupportingEvidenceKeepsBaseScore() {
        assertEquals(20, calculator.calculate(candidate(
                List.of(), List.of(), List.of(), List.of(), List.of()
        )));
    }

    @Test
    void supportingMaterialsAreCountedUpToTwoEach() {
        // 20 + 증거물 30 + 진술 20 = 70
        assertEquals(70, calculator.calculate(candidate(
                List.of("EV-1", "EV-2", "EV-3"),
                List.of(1, 2, 3),
                List.of(),
                List.of(),
                List.of()
        )));
    }

    @Test
    void corroborationAddsTenPoints() {
        // 20 + 증거물 15 + 진술 10 + 교차 확인 10 = 55
        assertEquals(55, calculator.calculate(candidate(
                List.of("EV-1"),
                List.of(1),
                List.of(),
                List.of(),
                List.of(new HypothesisAiCandidate.Corroboration(
                        "EV-1", 1, "같은 차량을 확인"
                ))
        )));
    }

    @Test
    void opposingMaterialsReduceScore() {
        // 20 + 증거물 30 + 진술 20 - 반대 근거 30 = 40
        assertEquals(40, calculator.calculate(candidate(
                List.of("EV-1", "EV-2"),
                List.of(1, 2),
                List.of("EV-3"),
                List.of(3),
                List.of()
        )));
    }

    @Test
    void scoreNeverFallsBelowFive() {
        assertEquals(5, calculator.calculate(candidate(
                List.of(),
                List.of(),
                List.of("EV-1", "EV-2"),
                List.of(1, 2),
                List.of()
        )));
    }

    private HypothesisAiCandidate candidate(
            List<String> evidenceIds,
            List<Integer> statementIds,
            List<String> opposingEvidenceIds,
            List<Integer> opposingStatementIds,
            List<HypothesisAiCandidate.Corroboration> corroborations
    ) {
        return new HypothesisAiCandidate(
                "테스트 가설",
                "테스트 판단 근거",
                null,
                evidenceIds,
                statementIds,
                List.of(),
                opposingEvidenceIds,
                opposingStatementIds,
                corroborations,
                List.of()
        );
    }
}