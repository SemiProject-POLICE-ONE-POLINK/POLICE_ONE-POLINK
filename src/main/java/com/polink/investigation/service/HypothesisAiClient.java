package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;

import java.util.List;

// 사건 자료를 AI에 보내고 가설 후보를 받는 역할
public interface HypothesisAiClient {

    // 입력: 해당 사건의 증거물·진술·수사기록
    // 결과: 아직 저장되지 않은 AI 가설 후보 목록
    List<HypothesisCandidateResponse> generate(
            HypothesisAiInput input
    );
}
