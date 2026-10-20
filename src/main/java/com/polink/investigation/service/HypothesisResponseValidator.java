package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// AI가 반환한 가설의 필수값과 근거 번호 검증
@Component
public class HypothesisResponseValidator {

    public void validate(
            HypothesisAiInput input,
            List<HypothesisCandidateResponse> hypotheses
    ) {

        // 1. 가설이 없거나 3개를 초과하면 거부
        if (hypotheses == null
                || hypotheses.isEmpty()
                || hypotheses.size() > 3) {
            throw invalidResponse();
        }

        // 2. AI에 전달한 증거 번호를 조회용 Set에 보관
        Set<String> allowedEvidenceIds = new HashSet<>();

        for (HypothesisAiInput.EvidenceInput evidence : input.evidences()) {
            allowedEvidenceIds.add(evidence.evidenceId());
        }

        // 3. AI에 전달한 진술 번호를 조회용 Set에 보관
        Set<Integer> allowedStatementIds = new HashSet<>();

        for (HypothesisAiInput.StatementInput statement : input.statements()) {
            allowedStatementIds.add(statement.statementId());
        }

        // 4. 가설 후보를 하나씩 검사
        for (HypothesisCandidateResponse hypothesis : hypotheses) {

            // 후보 자체가 없으면 거부
            if (hypothesis == null) {
                throw invalidResponse();
            }

            // 가설 내용과 판단 근거는 필수
            if (hypothesis.content() == null
                    || hypothesis.content().isBlank()
                    || hypothesis.reason() == null
                    || hypothesis.reason().isBlank()) {
                throw invalidResponse();
            }
            // 신뢰도는 0~100 범위의 정수
            Integer confidence = hypothesis.confidence();

            if (confidence == null
                    || confidence < 0
                    || confidence > 100) {
                throw invalidResponse();
            }

            // 근거가 없을 때는 null이 아닌 빈 목록으로 받아야 함
            if (hypothesis.evidenceIds() == null
                    || hypothesis.statementIds() == null) {
                throw invalidResponse();
            }

            // 입력 자료에 없는 증거물을 근거로 제시하면 거부
            for (String evidenceId : hypothesis.evidenceIds()) {
                if (evidenceId == null
                        || evidenceId.isBlank()
                        || !allowedEvidenceIds.contains(evidenceId)) {
                    throw invalidResponse();
                }
            }

            // 입력 자료에 없는 진술을 근거로 제시하면 거부
            for (Integer statementId : hypothesis.statementIds()) {
                if (statementId == null
                        || !allowedStatementIds.contains(statementId)) {
                    throw invalidResponse();
                }
            }
        }
    }

    // 외부 AI가 올바르지 않은 응답을 반환한 경우 사용
    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 분석 결과를 확인할 수 없습니다. 다시 시도해주세요."
        );
    }
}
