package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
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
            List<HypothesisAiCandidate> hypotheses
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

        // AI에 전달한 수사기록 번호 보관
        Set<Integer> allowedLogIds = new HashSet<>();

        for (HypothesisAiInput.InvestigationLogInput log
                : input.investigationLogs()) {
            allowedLogIds.add(log.logId());
        }

        // 4. 가설 후보를 하나씩 검사
        for (HypothesisAiCandidate hypothesis : hypotheses) {

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

            // 근거가 없을 시 null이 아닌 빈 목록으로 반환
            if (hypothesis.evidenceIds() == null
                    || hypothesis.statementIds() == null
                    || hypothesis.logIds() == null
                    || hypothesis.opposingEvidenceIds() == null     // 가설과 반대되는 증거 번호
                    || hypothesis.opposingStatementIds() == null    // 가설과 반대되는 진술 번호
                    || hypothesis.corroborations() == null
                    || hypothesis.circumstances() == null) {
                throw invalidResponse();
            }

            // 정황이 있다면 각 문장에 실제 내용 존재 필요
            for (String circumstance : hypothesis.circumstances()) {
                if (circumstance == null || circumstance.isBlank()) {
                    throw invalidResponse();
                }
            }

            // 입력 자료에 없는 증거물을 근거로 제시할 시 오류 발생
            for (String evidenceId : hypothesis.evidenceIds()) {
                if (evidenceId == null
                        || evidenceId.isBlank()
                        || !allowedEvidenceIds.contains(evidenceId)) {
                    throw invalidResponse();
                }
            }

            // 입력 자료에 없는 진술을 근거로 제시할 시 오류 발생
            for (Integer statementId : hypothesis.statementIds()) {
                if (statementId == null
                        || !allowedStatementIds.contains(statementId)) {
                    throw invalidResponse();
                }
            }

            // 입력에 없는 수사기록을 근거로 제시하면 거부
            for (Integer logId : hypothesis.logIds()) {
                if (logId == null
                        || !allowedLogIds.contains(logId)) {
                    throw invalidResponse();
                }
            }

            // 입력 자료에 없는 증거물을 반대 근거로 제시할 시 오류 발생
            for (String evidenceId : hypothesis.opposingEvidenceIds()) {
                if (evidenceId == null
                        || evidenceId.isBlank()
                        || !allowedEvidenceIds.contains(evidenceId)) {
                    throw invalidResponse();
                }
            }

            // 입력 자료에 없는 진술을 반대 근거로 제시할 시 오류 발생
            for (Integer statementId : hypothesis.opposingStatementIds()) {
                if (statementId == null
                        || !allowedStatementIds.contains(statementId)) {
                    throw invalidResponse();
                }
            }

            // 교차 확인에 사용한 증거물·진술이 실제로 가설의 지지 근거인지 검사
            for (HypothesisAiCandidate.Corroboration corroboration
                    : hypothesis.corroborations()) {

                if (corroboration == null
                        || corroboration.sharedFact() == null
                        || corroboration.sharedFact().isBlank()
                        || !hypothesis.evidenceIds().contains(corroboration.evidenceId())
                        || !hypothesis.statementIds().contains(corroboration.statementId())) {
                    throw invalidResponse();
                }
            }

            // 같은 자료 번호가 반복되어 점수에 중복 반영되지 않도록 검사
            validateNoDuplicateIds(hypothesis.evidenceIds());
            validateNoDuplicateIds(hypothesis.statementIds());
            validateNoDuplicateIds(hypothesis.logIds());
            validateNoDuplicateIds(hypothesis.opposingEvidenceIds());
            validateNoDuplicateIds(hypothesis.opposingStatementIds());
        }
    }

    // 한 목록에 같은 번호가 두 번 이상 있으면 AI 응답을 사용하지 않는다.
    private <T> void validateNoDuplicateIds(List<T> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalidResponse();
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
