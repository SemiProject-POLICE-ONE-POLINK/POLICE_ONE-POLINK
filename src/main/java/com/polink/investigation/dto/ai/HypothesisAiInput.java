package com.polink.investigation.dto.ai;

import com.polink.investigation.entity.StatementType;

import java.time.LocalDateTime;
import java.util.List;

// 한 사건의 자료 중 AI 가설 생성에 필요한 전체 정보
public record HypothesisAiInput(
        String caseId,                                  // 추론할 사건 번호
        String caseTitle,                               // 사건명
        String caseDescription,                         // 사건의 전체 내용
        List<EvidenceInput> evidences,                  // 해당 사건의 증거물 목록
        List<StatementInput> statements,                // 해당 사건 목격자들의 진술 목록
        List<InvestigationLogInput> investigationLogs,  // 해당 사건의 수사기록 목록
        List<String> previousHypotheses                 // 이전 가설 내용
) {

    // 기존 6개 인자로 생성할 때는 이전 가설 목록을 빈 목록으로 설정
    public HypothesisAiInput(
            String caseId,
            String caseTitle,
            String caseDescription,
            List<EvidenceInput> evidences,
            List<StatementInput> statements,
            List<InvestigationLogInput> investigationLogs
    ) {
        this(
                caseId,
                caseTitle,
                caseDescription,
                evidences,
                statements,
                investigationLogs,
                List.of()
        );
    }

    // AI가 증거물을 이해하고 근거 번호를 지목할 수 있도록 전달
    public record EvidenceInput(
            String evidenceId,                // 증거물 번호
            String name,                      // 증거물 이름
            String description                // 증거물에 대한 설명
    ) {
    }

    // AI가 진술을 읽고 근거 번호를 지목할 수 있도록 전달
    public record StatementInput(
            Integer statementId,              // 진술 번호
            String witnessId,            // 같은 목격자의 진술을 구분할 번호
            StatementType statementType, // 최초·추가·정정·번복 구분
            LocalDateTime createdAt,     // 진술 등록 일시
            String content               // 진술 내용
    ) {

    }

    // 이전 수사 내용을 AI 추론에 포함
    public record InvestigationLogInput(
            Integer logId,                    // 수사기록 번호
            String content,                    // 경찰관이 작성한 수사 내용
            LocalDateTime investigatedAt   // 수사를 진행한 일시
    ) {
    }
}
