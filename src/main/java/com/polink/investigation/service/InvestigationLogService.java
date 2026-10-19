package com.polink.investigation.service;

import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.CaseExistenceRepository;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvestigationLogService {

    private final InvestigationLogRepository investigationLogRepository;
    private final CaseExistenceRepository caseExistenceRepository;
    private final UserRepository userRepository;


    public InvestigationLogService(InvestigationLogRepository investigationLogRepository, CaseExistenceRepository caseExistenceRepository, UserRepository userRepository) {
        this.investigationLogRepository = investigationLogRepository;
        this.caseExistenceRepository = caseExistenceRepository;
        this.userRepository = userRepository;
    }

    // 사건별 수사기록 등록
    @Transactional
    public InvestigationLogResponse createInvestigationLog(
            String caseId,
            Integer loggedInUserId,
            InvestigationLogCreateRequest request
    ) {
        // 로그인한 사용자 확인
        User officer = findLoggedInUser(loggedInUserId);

        // 경찰관만 수사기록 등록 가능
        // 담당자 배정 기능 확인 후 수정 예정
        if (officer.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사기록을 등록할 권한이 없습니다."
            );
        }

        // 기록을 등록할 사건 확인
        if (!caseExistenceRepository.existsByCaseId(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "사건 정보를 확인할 수 없어 수사기록을 등록할 수 없습니다."
            );
        }

        InvestigationLog investigationLog =
                new InvestigationLog(
                        caseId,
                        officer,
                        request.content(),
                        request.investigatedAt()
                );

        InvestigationLog savedLog =
                investigationLogRepository.save(investigationLog);

        return InvestigationLogResponse.from(savedLog);
    }


    // 로그인 사용자 번호로 회원 조회
    private User findLoggedInUser(Integer loggedInUserId) {

        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "수사기록을 등록하려면 로그인이 필요합니다."
            );
        }

        return userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 정보를 확인할 수 없습니다."
                ));
    }
}
