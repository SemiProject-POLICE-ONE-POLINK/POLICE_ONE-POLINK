package com.polink.investigation.controller;

import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
import com.polink.investigation.service.InvestigationLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/investigations")
public class InvestigationLogController {

    private final InvestigationLogService investigationLogService;

    public InvestigationLogController(InvestigationLogService investigationLogService) {
        this.investigationLogService = investigationLogService;
    }

    // 사건별 수사기록 등록
    @PostMapping
    public ResponseEntity<InvestigationLogResponse>
    createInvestigationLog(
            @PathVariable("caseId") String caseId,

            // 검증된 Bearer 토큰 — 토큰 없이 요청 시 null
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody
            InvestigationLogCreateRequest request
    ) {
        InvestigationLogResponse response =
                investigationLogService.createInvestigationLog(
                        caseId,
                        toLoggedInUserId(jwt),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 사건별 수사기록 목록 조회
    @GetMapping
    public ResponseEntity<List<InvestigationLogResponse>> getFindInvestigationLogs(
            @PathVariable("caseId") String caseId,

            // 검증된 Bearer 토큰 — 토큰 없이 요청 시 null
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<InvestigationLogResponse> response =
                investigationLogService.findInvestigationLogs(caseId, toLoggedInUserId(jwt));

        return ResponseEntity.ok(response);
    }

    // 토큰의 sub(회원 번호)를 로그인 사용자로 사용, 토큰이 없을 시 null → Service가 401 처리
    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null ? null : Integer.valueOf(jwt.getSubject());
    }
}
