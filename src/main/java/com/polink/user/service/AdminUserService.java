package com.polink.user.service;

import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.dto.response.AssignablePoliceListResponse;
import com.polink.user.dto.response.AssignablePoliceResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;

    public AdminUserService(
            UserRepository userRepository,
            CaseRepository caseRepository
    ) {
        this.userRepository = userRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public AssignablePoliceListResponse getAssignablePolice(
            Integer adminId
    ) {
        if (adminId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "배정 가능한 경찰관 목록 조회는 관리자만 가능합니다."
            );
        }

        List<AssignablePoliceResponse> officers =
                userRepository.findByRoleOrderByNameAsc(
                                UserRole.POLICE
                        )
                        .stream()
                        .map(officer -> new AssignablePoliceResponse(
                                officer.getUserId(),
                                officer.getName(),
                                caseRepository
                                        .countByAssignedOfficer_UserIdAndStatus(
                                                officer.getUserId(),
                                                CaseStatus.INVESTIGATING
                                        )
                        ))
                        .toList();

        return new AssignablePoliceListResponse(officers);
    }
}