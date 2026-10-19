package com.polink.casefile.service;

import com.polink.casefile.dto.response.MyCaseListResponse;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.response.CaseCreateResponse;
import com.polink.casefile.entity.CaseFile;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.polink.casefile.dto.response.CaseDetailResponse;
import java.util.Objects;
import com.polink.casefile.dto.response.CaseListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.polink.casefile.dto.request.CaseOfficerRequest;
import com.polink.casefile.dto.response.CaseOfficerResponse;
import com.polink.casefile.entity.CaseStatus;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseNumberGenerator caseNumberGenerator;

    public CaseService(CaseRepository caseRepository,
                       UserRepository userRepository,
                       CaseNumberGenerator caseNumberGenerator) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.caseNumberGenerator = caseNumberGenerator;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CaseCreateResponse createCase(
            Integer reporterId,
            CaseCreateRequest request) {

        User reporter = validateReporter(reporterId);

        String caseId = caseNumberGenerator.generate();

        CaseFile caseFile = new CaseFile(
                caseId,
                reporter,
                request.getTitle(),
                request.getCaseType(),
                request.getDescription(),
                request.getOccurredAt(),
                request.getLocation()
        );

        CaseFile savedCase = caseRepository.save(caseFile);

        return new CaseCreateResponse(
                savedCase.getCaseId(),
                savedCase.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public CaseDetailResponse getCaseDetail(
            Integer userId,
            String caseId) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건을 찾을 수 없습니다."
                ));

        boolean canAccess =
                user.getRole() == UserRole.POLICE
                        || user.getRole() == UserRole.ADMIN
                        || (user.getRole() == UserRole.CITIZEN
                        && Objects.equals(
                        caseFile.getReporter().getUserId(),
                        user.getUserId()
                ));

        if (!canAccess) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 사건을 조회할 권한이 없습니다."
            );
        }

        return new CaseDetailResponse(caseFile);
    }

    @Transactional(readOnly = true)
    public MyCaseListResponse getMyCases(
            Integer userId,
            Pageable pageable) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        Page<CaseFile> cases;

        if (user.getRole() == UserRole.CITIZEN) {
            cases = caseRepository.findByReporter_UserId(
                    userId, pageable);
        } else if (user.getRole() == UserRole.POLICE) {
            cases = caseRepository.findByAssignedOfficer_UserId(
                    userId, pageable);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "내 사건 조회는 시민과 경찰만 가능합니다."
            );
        }

        return new MyCaseListResponse(cases);
    }

    @Transactional(readOnly = true)
    public CaseListResponse getCases(
            Integer userId,
            Pageable pageable) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        Page<CaseFile> cases;

        if (user.getRole() == UserRole.CITIZEN) {
            cases = caseRepository.findByReporter_UserId(
                    userId, pageable);
        } else if (user.getRole() == UserRole.POLICE
                || user.getRole() == UserRole.ADMIN) {
            cases = caseRepository.findAll(pageable);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "사건 목록을 조회할 권한이 없습니다."
            );
        }

        return new CaseListResponse(cases);
    }

    @Transactional
    public CaseOfficerResponse assignOfficer(
            Integer adminId,
            String caseId,
            CaseOfficerRequest request) {

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
                    "담당 경찰 배정은 관리자만 가능합니다."
            );
        }

        User officer = userRepository.findById(request.getOfficerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "배정할 회원을 찾을 수 없습니다."
                ));

        if (officer.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "경찰 회원만 담당자로 배정할 수 있습니다."
            );
        }

        CaseFile caseFile = caseRepository.findByCaseIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건을 찾을 수 없습니다."
                ));

        if (caseFile.getStatus() == CaseStatus.CLOSED
                || caseFile.getStatus() == CaseStatus.CANCELED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "종결 또는 취소된 사건은 담당 경찰을 변경할 수 없습니다."
            );
        }

        User currentOfficer = caseFile.getAssignedOfficer();

        if (currentOfficer == null) {
            if (caseFile.getStatus() != CaseStatus.WAITING) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "최초 배정은 대기 중인 사건만 가능합니다."
                );
            }
        } else if (Objects.equals(
                currentOfficer.getUserId(), officer.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "현재 담당 경찰과 동일한 경찰입니다."
            );
        }

        caseFile.assignOfficer(officer);

        return new CaseOfficerResponse(caseFile);
    }

    private User validateReporter(Integer reporterId) {
        if (reporterId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "신고자 회원을 찾을 수 없습니다."
                ));

        if (reporter.getRole() != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "사건 접수는 시민만 가능합니다."
            );
        }

        return reporter;
    }
}