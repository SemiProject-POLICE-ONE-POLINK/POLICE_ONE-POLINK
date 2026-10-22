package com.polink.user.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import com.polink.user.dto.response.PoliceDashboardResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PoliceDashboardService {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ItemRepository itemRepository;

    public PoliceDashboardService(UserRepository userRepository, CaseRepository caseRepository, ReturnRequestRepository returnRequestRepository, ItemRepository itemRepository) {
        this.userRepository = userRepository;
        this.caseRepository = caseRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public PoliceDashboardResponse getPoliceDashboard(Integer loggedInUserId) {
        // 로그인 여부 확인
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        User officer = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보를 확인할 수 없습니다."
                ));

        // 시민·관리자는 경찰 대시보드 조회 불가
        if (officer.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰만 대시보드를 조회할 수 있습니다."
            );
        }

        // 이 경찰에게 배정된 사건 전체 가져오기
        List<CaseFile> caseFiles = new ArrayList<>(
                caseRepository.findByAssignedOfficer_UserId(
                        loggedInUserId,
                        Pageable.unpaged()
                ).getContent()
        );

        // 최근 접수된 사건이 목록 위에 오도록 정렬
        caseFiles.sort(
                Comparator.comparing(CaseFile::getCreatedAt)
                        .reversed()
                        .thenComparing(
                                CaseFile::getCaseId,
                                Comparator.reverseOrder()
                        )
        );

        // 내게 배정된 사건 중 수사 중인 사건의 수를 계산
        long investigatingCaseCount = caseFiles.stream()
                .filter(caseFile ->
                        caseFile.getStatus() == CaseStatus.INVESTIGATING)
                .count();

        // 내 배정 사건을 화면의 표에 필요한 정보로 변환
        List<PoliceDashboardResponse.AssignedCase> assignedCases =
                caseFiles.stream()
                        .map(caseFile ->
                                new PoliceDashboardResponse.AssignedCase(
                                        caseFile.getCaseId(),
                                        caseFile.getTitle(),
                                        caseFile.getCaseType(),
                                        caseFile.getStatus()
                                )
                        )
                        .toList();


        // 모든 경찰관에게 공통으로 표시할 대기 중인 반환 신청 건수를 조회
        long pendingReturnRequestCount = returnRequestRepository
                .findReturnRequestsByConditions(
                        ReturnRequestStatus.PENDING,
                        null,
                        PageRequest.of(0, 1)
                )
                .getTotalElements();

        // 모든 경찰에게 공통으로 표시할 보관 중인 습득물 건수를 조회
        long storedFoundItemCount = itemRepository
                .findItemsByConditions(
                        ItemType.FOUND,
                        null,
                        null,
                        null,
                        ItemStatus.STORED,
                        null,
                        null,
                        PageRequest.of(0, 1)
                )
                .getTotalElements();

        return new PoliceDashboardResponse(
                officer.getName(),
                investigatingCaseCount,
                caseFiles.size(),
                pendingReturnRequestCount,
                storedFoundItemCount,
                assignedCases
        );
    }
}
