package com.polink.investigation.controller;


import com.polink.investigation.dto.response.InvestigationBoardResponse;
import com.polink.investigation.service.InvestigationBoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/investigation-board")
public class InvestigationBoardController {

    private final InvestigationBoardService investigationBoardService;

    public InvestigationBoardController(InvestigationBoardService investigationBoardService) {
        this.investigationBoardService = investigationBoardService;
    }

    @GetMapping
    public ResponseEntity<InvestigationBoardResponse>
    getInvestigationBoard(
            @PathVariable("caseId")
            String caseId,

            // 인증 기능 연동 전 Postman 테스트용 임시 헤더
            // 로그인 구현 후 인증 사용자 정보로 교체하고 제거 예정
            @RequestHeader(
                    value = "X-User-Id",
                    required = false
            )
            Integer loggedInUserId
    ) {
        InvestigationBoardResponse response =
                investigationBoardService.findInvestigationBoard(
                        caseId,
                        loggedInUserId
                );

        return ResponseEntity.ok(response);
    }
}
