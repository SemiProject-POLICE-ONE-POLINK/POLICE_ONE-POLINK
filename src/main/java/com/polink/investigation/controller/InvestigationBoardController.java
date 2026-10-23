package com.polink.investigation.controller;


import com.polink.investigation.dto.response.InvestigationBoardResponse;
import com.polink.investigation.service.InvestigationBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "수사")
@RestController
@RequestMapping("/api/v1/cases/{caseId}/investigation-board")
public class InvestigationBoardController {

    private final InvestigationBoardService investigationBoardService;

    public InvestigationBoardController(InvestigationBoardService investigationBoardService) {
        this.investigationBoardService = investigationBoardService;
    }

    @Operation(summary = "수사보드 조회")
    @GetMapping
    public ResponseEntity<InvestigationBoardResponse>
    getInvestigationBoard(
            @PathVariable("caseId")
            String caseId,

            // 검증된 Bearer 토큰 — 토큰 없이 요청 시 null
            @AuthenticationPrincipal
            Jwt jwt
    )
    {
        // 토큰의 sub(회원 번호)를 로그인 사용자로 사용, 없을 시 401 처리
        Integer loggedInUserId =
                jwt == null ? null : Integer.valueOf(jwt.getSubject());

        InvestigationBoardResponse response =
                investigationBoardService.findInvestigationBoard(
                        caseId,
                        loggedInUserId
                );

        return ResponseEntity.ok(response);
    }
}
