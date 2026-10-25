package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// ai-mock 프로필에서만 사용하는 가짜 AI 응답(가짜 후보로 테스트)
@Component
@Profile("ai-mock")
public class MockHypothesisAiClient implements HypothesisAiClient {

    // 실제 AI 호출 없이 테스트용 가설 후보 3개 반환
    @Override
    public List<HypothesisAiCandidate> generate(
            HypothesisAiInput input
    ) {

        // 입력에 실제 존재하는 증거물 번호 수집
        List<String> evidenceIds = new ArrayList<>();

        for (HypothesisAiInput.EvidenceInput evidence : input.evidences()) {
            evidenceIds.add(evidence.evidenceId());
        }

        // 입력에 실제 존재하는 진술 번호 수집
        List<Integer> statementIds = new ArrayList<>();

        for (HypothesisAiInput.StatementInput statement : input.statements()) {
            statementIds.add(statement.statementId());
        }

        // 입력에 존재하는 수사기록 번호 수집
        List<Integer> logIds = new ArrayList<>();

        for (HypothesisAiInput.InvestigationLogInput log
                : input.investigationLogs()) {
            logIds.add(log.logId());
        }

        // 모의 AI는 신뢰도를 정하지 않고 원본 후보만 반환한다.
        return List.of(
                new HypothesisAiCandidate(
                        "[테스트용] 가설 후보 1",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "등록된 증거물의 내용을 확인해주세요.",
                        evidenceIds,
                        statementIds,
                        logIds,
                        List.of(), // 반대 증거물
                        List.of(), // 반대 진술
                        List.of(), // 증거물·진술 교차 확인
                        List.of("[테스트용] 사건 자료를 함께 검토할 필요가 있습니다.")
                ),
                new HypothesisAiCandidate(
                        "[테스트용] 가설 후보 2",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "목격자의 진술 이력을 확인해주세요.",
                        evidenceIds,
                        statementIds,
                        logIds,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("[테스트용] 사건 자료를 함께 검토할 필요가 있습니다.")
                ),
                new HypothesisAiCandidate(
                        "[테스트용] 가설 후보 3",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "기존 수사기록과 사건 내용을 확인해주세요.",
                        evidenceIds,
                        statementIds,
                        logIds,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("[테스트용] 사건 자료를 함께 검토할 필요가 있습니다.")
                )
        );
    }
}