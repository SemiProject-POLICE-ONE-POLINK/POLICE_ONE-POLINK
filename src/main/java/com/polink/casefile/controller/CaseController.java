package com.polink.casefile.controller;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.response.CaseCloseResponse;
import com.polink.casefile.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping("/{caseId}/close")
    public ResponseEntity<CaseCloseResponse> closeCase(
            @PathVariable("caseId") String caseId,
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer adminId,
            @Valid @RequestBody CaseCloseRequest request
    ) {
        CaseCloseResponse response =
                caseService.closeCase(adminId, caseId, request);

        return ResponseEntity.ok(response);
    }
}
