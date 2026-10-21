package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;
import com.polink.casefile.entity.CaseType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@Profile("ai-mock")
public class MockSimilarCaseAiClient implements SimilarCaseAiClient {

    @Override
    public SimilarCaseAiResult analyze(SimilarCaseAiInput input) {
        CaseType suggestedCaseType = classify(input.situation());

        List<SimilarCaseAiResult.Match> matches = input.candidates().stream()
                .filter(candidate -> candidate.caseType() == suggestedCaseType)
                .limit(3)
                .map(candidate -> new SimilarCaseAiResult.Match(
                        candidate.caseId(),
                        "입력 상황과 사건 유형이 "
                                + caseTypeName(suggestedCaseType)
                                + "으로 같습니다."
                ))
                .toList();

        return new SimilarCaseAiResult(
                "입력한 상황은 "
                        + caseTypeName(suggestedCaseType)
                        + " 유형으로 판단됩니다.",
                suggestedCaseType,
                recommendation(suggestedCaseType),
                matches
        );
    }

    private CaseType classify(String situation) {
        String text = situation.toLowerCase(Locale.ROOT);

        if (containsAny(text, "사기", "송금", "입금", "거래", "보이스피싱")) {
            return CaseType.FRAUD;
        }

        if (containsAny(text, "실종", "연락두절", "사라졌")) {
            return CaseType.MISSING;
        }

        if (containsAny(text, "해킹", "계정", "온라인", "악성코드", "랜섬웨어")) {
            return CaseType.CYBER;
        }

        if (containsAny(text, "절도", "도난", "훔", "분실")) {
            return CaseType.THEFT;
        }

        return CaseType.ETC;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String caseTypeName(CaseType caseType) {
        return switch (caseType) {
            case THEFT -> "절도";
            case FRAUD -> "사기";
            case MISSING -> "실종";
            case CYBER -> "사이버 범죄";
            case ETC -> "기타";
        };
    }

    private String recommendation(CaseType caseType) {
        return switch (caseType) {
            case THEFT -> "도난 물품 정보와 마지막으로 확인한 장소를 기록해 주세요.";
            case FRAUD -> "송금 내역과 상대방과의 대화 내용을 보관해 주세요.";
            case MISSING -> "마지막 목격 장소와 연락 시각을 확인해 주세요.";
            case CYBER -> "접속 기록과 관련 화면을 캡처하고 비밀번호를 변경해 주세요.";
            case ETC -> "발생 일시, 장소, 상황을 구체적으로 정리해 주세요.";
        };
    }
}