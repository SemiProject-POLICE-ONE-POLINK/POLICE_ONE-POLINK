package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

// 수사 기록 Service
@Service
public class InvestigationLogService {

    private final InvestigationLogRepository investigationLogRepository;
    private final InvestigationAccessService investigationAccessService;

    public InvestigationLogService(InvestigationLogRepository investigationLogRepository, InvestigationAccessService investigationAccessService) {
        this.investigationLogRepository = investigationLogRepository;
        this.investigationAccessService = investigationAccessService;
    }

    // 사건별 수사기록 등록
    @Transactional
    public InvestigationLogResponse createInvestigationLog(
            String caseId,
            Integer loggedInUserId,
            InvestigationLogCreateRequest request
    ) {
        // 1. 로그인한 사용자가 수사중인 사건의 담당 경찰인지 확인
        CaseFile caseFile =
                investigationAccessService.findInvestigatingCase(
                        caseId,
                        loggedInUserId
                );

        // 2. 접근 검사를 통과한 사건의 담당 경찰을 작성자로 설정
        User officer = caseFile.getAssignedOfficer();

        // 3. 입력한 수사 내용과 수사 일시로 기록 생성
        InvestigationLog investigationLog =
                new InvestigationLog(
                        caseFile,
                        officer,
                        request.content(),
                        request.investigatedAt()
                );

        // 4. 저장 결과를 응답 DTO로 변환
        InvestigationLog savedLog =
                investigationLogRepository.save(investigationLog);

        return InvestigationLogResponse.from(savedLog);
    }

    // 담당 경찰이 사건 상태와 관계없이 수사기록 조회
    @Transactional(readOnly = true)
    public List<InvestigationLogResponse> findInvestigationLogs(
            String caseId,
            Integer loggedInUserId
    ) {

        // 1. 해당 사건의 담당 경찰인지 확인
        investigationAccessService.findAssignedCase(
                caseId,
                loggedInUserId
        );

        // 2. 수사 일시·기록 번호 오름차순 조회
        List<InvestigationLog> logs =
                investigationLogRepository
                        .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(
                                caseId
                        );

        // 3. 조회 결과를 응답 DTO 목록으로 변환
        List<InvestigationLogResponse> responses = new ArrayList<>();

        for (InvestigationLog log : logs) {
            responses.add(InvestigationLogResponse.from(log));
        }

        return responses;
    }
}