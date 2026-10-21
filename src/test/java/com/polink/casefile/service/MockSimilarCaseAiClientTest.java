package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;
import com.polink.casefile.entity.CaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockSimilarCaseAiClientTest {

    private final MockSimilarCaseAiClient aiClient =
            new MockSimilarCaseAiClient();

    @Test
    void analyze_fraudSituation_returnsFraudMatch() {
        SimilarCaseAiInput input = new SimilarCaseAiInput(
                "중고 거래 대금을 송금했지만 연락이 끊겼습니다.",
                List.of(
                        candidate(
                                "SMPA-2026-0001",
                                CaseType.FRAUD
                        ),
                        candidate(
                                "SMPA-2026-0002",
                                CaseType.THEFT
                        )
                )
        );

        SimilarCaseAiResult result = aiClient.analyze(input);

        assertEquals(CaseType.FRAUD, result.suggestedCaseType());
        assertEquals(1, result.matches().size());
        assertEquals(
                "SMPA-2026-0001",
                result.matches().get(0).caseId()
        );
    }

    @ParameterizedTest
    @CsvSource({
            "지갑을 도난당했습니다., THEFT",
            "가족이 연락두절되었습니다., MISSING",
            "계정이 해킹되었습니다., CYBER",
            "이웃과 말다툼이 있었습니다., ETC"
    })
    void analyze_classifiesSituation(
            String situation,
            CaseType expectedCaseType
    ) {
        SimilarCaseAiResult result = aiClient.analyze(
                new SimilarCaseAiInput(
                        situation,
                        List.of()
                )
        );

        assertEquals(
                expectedCaseType,
                result.suggestedCaseType()
        );
        assertEquals(0, result.matches().size());
    }

    private SimilarCaseAiInput.CaseCandidate candidate(
            String caseId,
            CaseType caseType
    ) {
        return new SimilarCaseAiInput.CaseCandidate(
                caseId,
                "테스트 사건",
                caseType,
                "테스트 사건 내용",
                LocalDateTime.of(2026, 10, 22, 10, 0),
                "테스트 장소"
        );
    }
}