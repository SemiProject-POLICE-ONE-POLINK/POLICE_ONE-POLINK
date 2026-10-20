package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
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
    public List<HypothesisCandidateResponse> generate(
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

        // 내용·근거·신뢰도는 분석 결과가 아닌 테스트용 값
        return List.of(
                new HypothesisCandidateResponse(
                        "[테스트용] 가설 후보 1",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "등록된 증거물의 내용을 확인해주세요.",
                        60,
                        evidenceIds,
                        statementIds
                ),
                new HypothesisCandidateResponse(
                        "[테스트용] 가설 후보 2",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "목격자의 진술 이력을 확인해주세요.",
                        50,
                        evidenceIds,
                        statementIds
                ),
                new HypothesisCandidateResponse(
                        "[테스트용] 가설 후보 3",
                        "실제 분석 없이 생성 화면과 근거 번호 전달을 확인하는 응답입니다.",
                        "기존 수사기록과 사건 내용을 확인해주세요.",
                        40,
                        evidenceIds,
                        statementIds
                )
        );
    }
}