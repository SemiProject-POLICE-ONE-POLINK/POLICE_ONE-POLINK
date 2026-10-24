package com.polink.investigation.controller;

import com.polink.investigation.dto.HypothesisChecklist;
import com.polink.investigation.dto.request.HypothesisChecklistUpdateRequest;
import com.polink.investigation.service.HypothesisChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "수사")
@RestController
@RequestMapping("/api/v1/cases/{caseId}/hypotheses/{hypothesisId}/checklist")
public class HypothesisChecklistController {

    private final HypothesisChecklistService checklistService;

    public HypothesisChecklistController(HypothesisChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    // 채택한 가설의 확인 항목과 체크 상태 조회
    @Operation(summary = "수사 가설 체크리스트 조회")
    @GetMapping
    public ResponseEntity<HypothesisChecklist> getChecklist(
            @PathVariable("caseId") String caseId,
            @PathVariable("hypothesisId") Integer hypothesisId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        HypothesisChecklist response =
                checklistService.findChecklist(
                        caseId,
                        hypothesisId,
                        toLoggedInUserId(jwt)
                );

        return ResponseEntity.ok(response);
    }

    // 선택한 확인 항목을 체크하거나 체크 해제
    @Operation(summary = "수사 가설 체크 상태 변경")
    @PatchMapping("/{checkItemId}")
    public ResponseEntity<HypothesisChecklist> updateCheckItem(
            @PathVariable("caseId") String caseId,
            @PathVariable("hypothesisId") Integer hypothesisId,
            @PathVariable("checkItemId") Integer itemId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HypothesisChecklistUpdateRequest request
    ) {
        HypothesisChecklist response =
                checklistService.updateCheckItem(
                        caseId,
                        hypothesisId,
                        itemId,
                        toLoggedInUserId(jwt),
                        request
                );

        return ResponseEntity.ok(response);
    }

    // 검증된 JWT의 회원 번호를 Service에 전달
    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}
