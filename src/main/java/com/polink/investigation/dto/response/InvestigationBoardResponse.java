package com.polink.investigation.dto.response;

import java.util.List;

// 사건별 수사보드 통합 조회 응답 DTO
public record InvestigationBoardResponse(

        BoardCaseResponse caseInfo,

        int totalEvidenceCount,
        List<BoardEvidenceSummaryResponse> evidences,

        int totalWitnessCount,
        List<BoardWitnessSummaryResponse> witnesses,

        int totalInvestigationLogCount,
        List<InvestigationLogResponse> investigationLogs,

        BoardHypothesisSummaryResponse latestHypothesis
)  {
}
