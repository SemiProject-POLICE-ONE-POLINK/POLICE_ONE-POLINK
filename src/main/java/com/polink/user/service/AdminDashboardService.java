package com.polink.user.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.dto.response.AdminDashboardResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;

    public AdminDashboardService(UserRepository userRepository, CaseRepository caseRepository) {
        this.userRepository = userRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard(Integer loggedInUserId) {
        // 로그인 여부 확인
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        User admin = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보를 확인할 수 없습니다."
                ));

        // 관리자만 관리자 대시보드를 조회
        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자만 대시보드를 조회할 수 있습니다."
            );
        }

        // 전체 사건을 읽어 상태별·유형별 건수를 계산
        List<CaseFile> caseFiles = caseRepository.findAll();

        long waitingCaseCount = 0;
        long investigatingCaseCount = 0;
        long closedCaseCount = 0;
        long canceledCaseCount = 0;

        // 사건이 없는 유형도 화면에 0건으로 표시할 수 있도록 준비
        Map<CaseType, Long> caseTypeCountByType =
                new EnumMap<>(CaseType.class);
        for (CaseType caseType : CaseType.values()) {
            caseTypeCountByType.put(caseType, 0L);
        }

        List<AdminDashboardResponse.UnassignedCase> unassignedCases =
                new ArrayList<>();

        for (CaseFile caseFile : caseFiles) {
            // 사건 상태별 전체 건수
            if (caseFile.getStatus() == CaseStatus.WAITING) {
                waitingCaseCount++;
            } else if (caseFile.getStatus() == CaseStatus.INVESTIGATING) {
                investigatingCaseCount++;
            } else if (caseFile.getStatus() == CaseStatus.CLOSED) {
                closedCaseCount++;
            } else if (caseFile.getStatus() == CaseStatus.CANCELED) {
                canceledCaseCount++;
            }

            // 사건 유형별 전체 건수
            CaseType caseType = caseFile.getCaseType();
            caseTypeCountByType.put(
                    caseType,
                    caseTypeCountByType.get(caseType) + 1
            );

            // 대기 중이면서 담당 경찰이 없는 사건만 배정 목록에 담기
            if (caseFile.getStatus() == CaseStatus.WAITING
                    && caseFile.getAssignedOfficer() == null) {
                unassignedCases.add(
                        new AdminDashboardResponse.UnassignedCase(
                                caseFile.getCaseId(),
                                caseFile.getTitle(),
                                caseType,
                                caseFile.getCreatedAt()
                        )
                );
            }
        }

        // 배정 대기 사건은 최근 접수순으로 표시
        unassignedCases.sort(
                Comparator.comparing(
                                AdminDashboardResponse.UnassignedCase::createdAt
                        )
                        .reversed()
                        .thenComparing(
                                AdminDashboardResponse.UnassignedCase::caseId,
                                Comparator.reverseOrder()
                        )
        );

        // 유형별 건수를 DTO 목록으로 변환
        List<AdminDashboardResponse.CaseTypeCount> caseTypeCounts =
                new ArrayList<>();

        for (CaseType caseType : CaseType.values()) {
            caseTypeCounts.add(
                    new AdminDashboardResponse.CaseTypeCount(
                            caseType,
                            caseTypeCountByType.get(caseType)
                    )
            );
        }

        return new AdminDashboardResponse(
                admin.getName(),
                waitingCaseCount,
                investigatingCaseCount,
                closedCaseCount,
                canceledCaseCount,
                unassignedCases.size(),
                unassignedCases,
                caseTypeCounts
        );
    }
}
