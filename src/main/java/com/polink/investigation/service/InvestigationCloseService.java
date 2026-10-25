package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.investigation.dto.request.InvestigationCloseRequest;
import com.polink.investigation.dto.response.InvestigationCloseResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InvestigationCloseService {

    private final InvestigationAccessService investigationAccessService;
    private final InvestigationLogRepository investigationLogRepository;

    public InvestigationCloseService(InvestigationAccessService investigationAccessService, InvestigationLogRepository investigationLogRepository) {
        this.investigationAccessService = investigationAccessService;
        this.investigationLogRepository = investigationLogRepository;
    }

    // 담당 경찰이 수사를 종결 — 사건은 관리자 종결 대기 상태로 변경
    @Transactional
    public InvestigationCloseResponse closeInvestigation(
            String caseId,
            Integer loggedInUserId,
            InvestigationCloseRequest request
    ) {
        // 1. 로그인 사용자가 수사중인 사건의 담당 경찰인지 확인
        CaseFile caseFile =
                investigationAccessService.findInvestigatingCase(
                        caseId,
                        loggedInUserId
                );

        User officer = caseFile.getAssignedOfficer();

        // 2. 사건 상태를 관리자 종결 대기로 변경
        caseFile.finishInvestigation();

        // 3. 누가·언제·왜 종결했는지 수사기록으로 남김
        InvestigationLog closeLog = new InvestigationLog(
                caseFile,
                officer,
                "수사 종결: " + request.closeReason(),
                LocalDateTime.now()
        );

        InvestigationLog savedLog =
                investigationLogRepository.save(closeLog);

        // 4. 바뀐 상태와 종결 기록으로 응답 생성
        return InvestigationCloseResponse.from(
                caseFile,
                savedLog,
                request.closeReason()
        );
    }
}

