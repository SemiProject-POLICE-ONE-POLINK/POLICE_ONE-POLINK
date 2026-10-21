package com.polink.investigation.controller;

import com.polink.investigation.dto.request.InvestigationCloseRequest;
import com.polink.investigation.dto.response.InvestigationCloseResponse;
import com.polink.investigation.service.InvestigationCloseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/investigation")
public class InvestigationCloseController {

    private final InvestigationCloseService investigationCloseService;

    public InvestigationCloseController(InvestigationCloseService investigationCloseService) {
        this.investigationCloseService = investigationCloseService;
    }

    // 담당 경찰의 수사 종결 — 사건을 관리자 종결 대기 상태로 변경
    @PostMapping("/close")
    public ResponseEntity<InvestigationCloseResponse> closeInvestigation(
            @PathVariable("caseId") String caseId,

            // 검증된 Bearer 토큰 — 토큰 없이 요청하면 null
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody InvestigationCloseRequest request
    ) {
        // 토큰의 회원 번호(sub)를 로그인 사용자 번호로 변환 — 토큰이 없으면 null을 넘기고 Service에서 401 처리
        Integer loggedInUserId =
                jwt == null ? null : Integer.valueOf(jwt.getSubject());

        InvestigationCloseResponse response =
                investigationCloseService.closeInvestigation(
                        caseId,
                        loggedInUserId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}