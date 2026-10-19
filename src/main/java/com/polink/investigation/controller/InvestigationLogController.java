package com.polink.investigation.controller;

import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
import com.polink.investigation.service.InvestigationLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

            // 인증 기능 연동 전 Postman 테스트용 임시 헤더
            // 로그인 구현 후 인증 사용자 정보로 교체 후 해당부분 제거 예정
            @RequestHeader(
                    value = "X-User-Id",
                    required = false
            )
            Integer loggedInUserId,
            @Valid @RequestBody
            InvestigationLogCreateRequest request
    ) {
        InvestigationLogResponse response =
                investigationLogService.createInvestigationLog(
                        caseId,
                        loggedInUserId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
