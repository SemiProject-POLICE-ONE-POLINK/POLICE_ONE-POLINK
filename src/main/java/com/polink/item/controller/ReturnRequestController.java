package com.polink.item.controller;

import com.polink.item.returnrequest.dto.MyReturnRequestListResponse;
import com.polink.item.returnrequest.dto.PoliceReturnRequestListResponse;
import com.polink.item.returnrequest.dto.ReturnRequestCreateRequest;
import com.polink.item.returnrequest.dto.ReturnRequestCreateResponse;
import com.polink.item.returnrequest.dto.ReturnRequestDetailResponse;
import com.polink.item.returnrequest.dto.ReturnRequestRejectRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.service.ReturnRequestService;
import com.polink.user.entity.UserRole;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.polink.item.returnrequest.dto.ReturnRequestCompleteResponse;
import com.polink.item.returnrequest.dto.ReturnRequestProcessResponse;

/*
 * JWT로 로그인한 시민·경찰이 반환 신청 업무를 처리하는 Controller다.
 * 시민은 반환 신청 등록과 본인 신청 목록 조회를 할 수 있다.
 * 경찰은 전체 신청 조회·상세 조회·승인·반려·반환 완료 처리를 할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/return-requests")
@RequiredArgsConstructor
public class ReturnRequestController {

    // 반환 신청의 등록·조회·상태 변경 등 실제 업무 규칙을 처리한다.
    private final ReturnRequestService returnRequestService;

    /*
     * POST /api/v1/return-requests
     * 시민이 본인이 등록한 분실물과 습득물을 연결해 반환을 신청한다.
     */
    @PostMapping
    public ResponseEntity<ReturnRequestCreateResponse> createReturnRequest(
            // Authorization 헤더의 Bearer 토큰에서 추출된 JWT다.
            @AuthenticationPrincipal Jwt jwt,

            // 분실물 번호·습득물 번호·신청 사유의 유효성을 검증한다.
            @Valid @RequestBody ReturnRequestCreateRequest request
    ) {
        // 토큰의 sub Claim에서 시민 회원 번호를 가져온다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 반환 신청 등록은 시민 계정만 가능하다.
        requireCitizen(jwt);

        // Service가 분실물 등록자 확인·상태·중복 신청 여부를 검증한다.
        ReturnRequestCreateResponse response =
                returnRequestService.createReturnRequest(currentUserId, request);

        // 새 반환 신청이 만들어졌으므로 201 Created와 상세 주소를 반환한다.
        return ResponseEntity
                .created(URI.create("/api/v1/return-requests/" + response.requestId()))
                .body(response);
    }

    /*
     * GET /api/v1/return-requests/my?page=0&size=10
     * 로그인한 시민이 본인이 신청한 반환 신청 목록만 조회한다.
     * 명세의 경로가 /my이므로 기존 /me 경로를 /my로 통일한다.
     */
    @GetMapping("/my")
    public ResponseEntity<MyReturnRequestListResponse> getMyReturnRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // 요청 파라미터가 아닌 JWT 사용자 ID만 사용하므로 타인 신청 조회를 막는다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 내 반환 신청 조회는 시민 계정만 가능하다.
        requireCitizen(jwt);

        return ResponseEntity.ok(
                returnRequestService.getMyReturnRequests(currentUserId, page, size)
        );
    }

    /*
     * GET /api/v1/return-requests
     * 경찰이 상태·검색어·페이지 조건으로 전체 반환 신청을 조회한다.
     */
    @GetMapping
    public ResponseEntity<PoliceReturnRequestListResponse> getReturnRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // 전체 반환 신청 목록은 경찰 계정만 조회할 수 있다.
        requirePolice(jwt);

        return ResponseEntity.ok(
                returnRequestService.getReturnRequests(status, keyword, page, size)
        );
    }

    /*
     * GET /api/v1/return-requests/{requestId}
     * 경찰이 반환 신청 한 건의 상세 정보와 연결된 물품 정보를 조회한다.
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<ReturnRequestDetailResponse> getReturnRequestDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer requestId
    ) {
        // 개인 정보가 포함될 수 있는 반환 신청 상세는 경찰만 볼 수 있다.
        requirePolice(jwt);

        return ResponseEntity.ok(
                returnRequestService.getReturnRequestDetail(requestId)
        );
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/approve
     * 경찰이 대기 중인 반환 신청을 승인한다.
     * 성공하면 200 OK와 함께 requestId, status, processedAt을 반환한다.
     */
    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<ReturnRequestProcessResponse> approveReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer requestId
    ) {
        // JWT에서 실제 처리한 경찰관 번호를 가져온다.
        Integer currentPoliceId = requireLoggedInUserId(jwt);

        // 승인 API는 경찰 역할만 호출할 수 있다.
        requirePolice(jwt);

        // Service가 상태 변경을 수행하고 처리 결과 DTO를 반환한다.
        ReturnRequestProcessResponse response =
                returnRequestService.approveReturnRequest(
                        currentPoliceId,
                        requestId
                );

        // 명세에 맞춰 204가 아니라 200 OK와 JSON 본문을 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/reject
     * 경찰이 대기 중인 반환 신청을 반려하고 반려 사유를 저장한다.
     * 성공하면 200 OK와 함께 requestId, status, processedAt을 반환한다.
     */
    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<ReturnRequestProcessResponse> rejectReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer requestId,
            @Valid @RequestBody ReturnRequestRejectRequest request
    ) {
        // JWT에서 실제 처리한 경찰관 번호를 가져온다.
        Integer currentPoliceId = requireLoggedInUserId(jwt);

        // 반려 API도 경찰 역할만 호출할 수 있다.
        requirePolice(jwt);

        // Service가 반려 사유 저장 및 상태 변경을 수행한다.
        ReturnRequestProcessResponse response =
                returnRequestService.rejectReturnRequest(
                        currentPoliceId,
                        requestId,
                        request.rejectReason()
                );

        // 명세에 맞춰 204가 아니라 200 OK와 JSON 본문을 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/complete
     * 경찰이 승인된 반환 신청의 실제 물품 인계를 완료 처리한다.
     */
    @PatchMapping("/{requestId}/complete")
    public ResponseEntity<ReturnRequestCompleteResponse> completeReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer requestId
    ) {
        // 반환 완료 처리는 경찰 계정만 할 수 있다.
        requirePolice(jwt);

        // Service가 상태 검증과 완료 처리를 수행한 뒤 완료 정보를 반환한다.
        ReturnRequestCompleteResponse response =
                returnRequestService.completeReturnRequest(requestId);

        // API 명세에 맞게 200 OK와 완료 신청 번호·완료 시각을 반환한다.
        return ResponseEntity.ok(response);
    }

    /**
     * JWT subject(sub)를 로그인 회원 번호로 변환한다.
     * 토큰이 없거나 회원 번호 형식이 맞지 않으면 401 Unauthorized를 반환한다.
     */
    private Integer requireLoggedInUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 올바르지 않습니다."
            );
        }
    }

    /*
     * JWT role Claim을 UserRole enum으로 변환한다.
     * 토큰의 역할 정보 누락·변조는 인증되지 않은 요청으로 보고 401을 반환한다.
     */
    private UserRole requireLoggedInUserRole(Jwt jwt) {
        // 토큰과 subject가 유효한지 먼저 확인한다.
        requireLoggedInUserId(jwt);

        String roleName = jwt.getClaimAsString("role");
        if (roleName == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 역할 정보가 없습니다."
            );
        }

        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 역할 정보가 올바르지 않습니다."
            );
        }
    }

    /** 시민 역할만 허용한다. 시민이 아니면 403 Forbidden을 반환한다. */
    private void requireCitizen(Jwt jwt) {
        if (requireLoggedInUserRole(jwt) != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    /** 경찰 역할만 허용한다. 경찰이 아니면 403 Forbidden을 반환한다. */
    private void requirePolice(Jwt jwt) {
        if (requireLoggedInUserRole(jwt) != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰 계정만 사용할 수 있는 기능입니다."
            );
        }
    }
}