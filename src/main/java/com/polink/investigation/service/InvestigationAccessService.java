package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvestigationAccessService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    public InvestigationAccessService(CaseRepository caseRepository, UserRepository userRepository) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    // 로그인한 사용자가 해당 사건의 담당 경찰관인지 확인
    @Transactional(readOnly = true)
    public CaseFile findAssignedCase(
            String caseId,
            Integer loggedInUserId
    ) {

        // 1. 로그인 정보 확인
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        User user = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 정보를 확인할 수 없습니다."
                ));

        // 2. 경찰 역할 확인
        if (user.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "담당 경찰만 수사 자료에 접근할 수 있습니다."
            );
        }

        // 3. 사건 존재 여부 확인
        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건 정보를 찾을 수 없습니다."
                ));

        // 4. 해당 사건에 배정된 경찰인지 확인
        User assignedOfficer = caseFile.getAssignedOfficer();

        if (assignedOfficer == null
                || !assignedOfficer.getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 사건의 담당 경찰만 접근할 수 있습니다."
            );
        }

        return caseFile;
    }

    // 담당 경찰관 확인 후 사건이 수사중인지 추가 검사
    @Transactional(readOnly = true)
    public CaseFile findInvestigatingCase(
            String caseId,
            Integer loggedInUserId
    ) {

        CaseFile caseFile =
                findAssignedCase(caseId, loggedInUserId);

        // 대기중·종결·취소 사건에서는 수사 작업 거부
        if (caseFile.getStatus() != CaseStatus.INVESTIGATING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "수사중인 사건에서만 이용할 수 있습니다."
            );
        }

        // TODO: 별도 수사 상태 구현 후 수사중 여부도 함께 확인
        // 사건이 수사중이어도 수사가 종결되었다면 작업 거부

        return caseFile;
    }
}