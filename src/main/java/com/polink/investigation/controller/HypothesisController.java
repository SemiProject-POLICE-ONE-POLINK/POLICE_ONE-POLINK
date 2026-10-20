package com.polink.investigation.controller;

import com.polink.investigation.dto.request.HypothesisSaveRequest;
import com.polink.investigation.dto.response.HypothesisResponse;
import com.polink.investigation.service.HypothesisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/hypotheses")
public class HypothesisController {

    private final HypothesisService hypothesisService;

    public HypothesisController(HypothesisService hypothesisService) {
        this.hypothesisService = hypothesisService;
    }

    // 경찰관이 선택한 AI 가설 후보를 채택하여 저장
    @PostMapping
    public ResponseEntity<HypothesisResponse> adoptHypothesis(
            @PathVariable("caseId") String caseId,

            // 인증 연동 전 테스트용 임시 헤더
            // JWT 연동 후 인증된 사용자 정보로 교체 예정
            @RequestHeader(value = "X-User-Id", required = false)
            Integer loggedInUserId,

            @Valid @RequestBody HypothesisSaveRequest request
    ) {
        HypothesisResponse response =
                hypothesisService.adoptHypothesis(
                        caseId,
                        loggedInUserId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
