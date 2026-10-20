package com.polink.investigation.controller;

import com.polink.investigation.dto.request.StatementCreateRequest;
import com.polink.investigation.dto.response.StatementResponse;
import com.polink.investigation.service.StatementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases/{caseId}/witnesses/{witnessId}/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }
    // 기존 목격자에게 추가·정정·번복 진술 등록
    @PostMapping
    public ResponseEntity<StatementResponse> createStatement(
            @PathVariable("caseId") String caseId,
            @PathVariable("witnessId") String witnessId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StatementCreateRequest request
    ) {

        // 사건번호·목격자 번호와 입력한 진술 정보를 Service에 전달
        StatementResponse response =
                statementService.createStatement(
                        caseId,
                        witnessId,
                        toLoggedInUserId(jwt),
                        request
                );

        // 등록된 진술 정보와 HTTP 201 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    // 검증된 JWT에서 회원 번호 추출 — 토큰이 없으면 Service에서 401 처리
    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}