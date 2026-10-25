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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 관리자는 감사 목적의 전체 신청 조회·상세 조회만 할 수 있고 상태 변경은 할 수 없다.
 */
@Tag(name = "분실물·습득물")
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
    @Operation(summary = "반환 신청")
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
     * GET /api/v1/return-requests/my?status=PENDING&page=0&size=10
     * 로그인한 시민이 본인의 반환 신청 목록만 조회한다.
     * status를 전달하면 해당 상태의 신청만 조회한다.
     */
    @Operation(summary = "내 반환 신청 목록 조회")
    @GetMapping("/my")
    public ResponseEntity<MyReturnRequestListResponse> getMyReturnRequests(
            @AuthenticationPrincipal Jwt jwt,

            // 선택한 상태의 본인 반환 신청만 필터링한다.
            // 예: PENDING, APPROVED, REJECTED, COMPLETED
            @RequestParam(required = false) ReturnRequestStatus status,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // 요청 파라미터가 아닌 JWT 사용자 ID만 사용하므로 타인 신청 조회를 막는다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 내 반환 신청 조회는 시민 계정만 가능하다.
        requireCitizen(jwt);

        return ResponseEntity.ok(
                returnRequestService.getMyReturnRequests(
                        currentUserId,
                        status,
                        page,
                        size
                )
        );
    }

    /*
     * GET /api/v1/return-requests/my/{requestId}
     *
     * 로그인한 시민이 본인이 만든 반환 신청 한 건의 상세 정보만 조회한다.
     * 경찰용 GET /{requestId}와 구분하기 위해 URL에 /my를 포함한다.
     */
    @Operation(summary = "내 반환 신청 상세 조회")
    @GetMapping("/my/{requestId}")
    public ResponseEntity<ReturnRequestDetailResponse> getMyReturnRequestDetail(
            // Authorization 헤더에서 인증된 시민 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // URL의 반환 신청 번호를 받는다.
            @PathVariable Integer requestId
    ) {
        // JWT의 sub Claim에서 로그인한 시민 회원 번호를 가져온다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 시민 계정만 자신의 반환 신청 상세를 조회할 수 있다.
        requireCitizen(jwt);

        // Service가 신청자 번호와 로그인 사용자 번호를 비교해
        // 본인 신청인지 확인한 뒤 상세 정보를 반환한다.
        return ResponseEntity.ok(
                returnRequestService.getMyReturnRequestDetail(
                        currentUserId,
                        requestId
                )
        );
    }

    /*
     * GET /api/v1/return-requests
     *
     * 경찰 또는 관리자가 전체 반환 신청을 조회한다.
     * 상태와 검색어는 선택값이며, 최신 신청순으로 페이지 조회한다.
     */
    @Operation(summary = "경찰·관리자용 반환 신청 목록 조회")
    @GetMapping
    public ResponseEntity<PoliceReturnRequestListResponse> getReturnRequests(
            // Authorization 헤더에서 인증된 경찰 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // 선택한 상태의 본인 반환 신청만 필터링한다.
            // 예: PENDING, APPROVED, REJECTED, COMPLETED
            @RequestParam(required = false) ReturnRequestStatus status,

            // 분실물 번호, 습득물 번호, 신청 사유로 검색할 수 있다.
            @RequestParam(required = false) String keyword,

            // 페이지 번호는 0부터 시작한다.
            @RequestParam(defaultValue = "0") int page,

            // 한 페이지에 보여줄 신청 개수다.
            @RequestParam(defaultValue = "10") int size
    ) {
        // 관리자는 조회만 허용한다. 승인·반려·완료 API는 아래에서 경찰만 검사한다.
        requirePoliceOrAdmin(jwt);

        // Service가 상태·검색어·페이징 조건으로 목록을 조회한다.
        return ResponseEntity.ok(
                returnRequestService.getReturnRequests(
                        status,
                        keyword,
                        page,
                        size
                )
        );
    }

    /*
     * GET /api/v1/return-requests/{requestId}
     * 경찰 또는 관리자가 반환 신청 한 건의 상세 정보와
     * 연결된 분실물·습득물 정보를 조회한다.
     */
    @Operation(summary = "경찰·관리자용 반환 신청 상세 조회")
    @GetMapping("/{requestId}")
    public ResponseEntity<ReturnRequestDetailResponse> getReturnRequestDetail(
            // Authorization 헤더에서 인증된 경찰 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // URL의 반환 신청 번호를 받는다.
            @PathVariable Integer requestId
    ) {
        // 관리자는 상세 확인까지만 가능하다.
        requirePoliceOrAdmin(jwt);

        // Service가 반환 신청과 연결된 두 물품 정보를 조회해 상세 DTO로 반환한다.
        return ResponseEntity.ok(
                returnRequestService.getReturnRequestDetail(requestId)
        );
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/approve

     * 경찰이 대기 상태의 반환 신청을 승인한다.
     * 승인되면 신청과 연결된 물품은 인계 대기 상태가 된다.
     */
    @Operation(summary = "반환 신청 승인")
    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<ReturnRequestProcessResponse> approveReturnRequest(
            // Authorization 헤더에서 인증된 경찰 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // URL의 반환 신청 번호를 받는다.
            @PathVariable Integer requestId
    ) {
        // JWT의 sub Claim에서 승인 처리 경찰관의 회원 번호를 가져온다.
        Integer currentPoliceId = requireLoggedInUserId(jwt);

        // 승인 처리는 경찰 계정만 가능하다.
        requirePolice(jwt);

        // Service가 PENDING 상태 검증, 상태 변경, 처리 시각 저장을 수행한다.
        ReturnRequestProcessResponse response =
                returnRequestService.approveReturnRequest(
                        currentPoliceId,
                        requestId
                );
        // 처리된 신청 번호, APPROVED 상태, 처리 시각을 200 OK로 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/reject
     * 경찰이 대기 상태의 반환 신청을 반려하고,
     * 반려 사유와 처리 정보를 저장한다.
     */
    @Operation(summary = "반환 신청 반려")
    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<ReturnRequestProcessResponse> rejectReturnRequest(
            // Authorization 헤더에서 인증된 경찰 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // URL의 반환 신청 번호를 받는다.
            @PathVariable Integer requestId,

            // 반려 사유를 JSON 요청 본문에서 받는다.
            @Valid @RequestBody ReturnRequestRejectRequest request
    ) {
        // JWT의 sub Claim에서 반려 처리 경찰관의 회원 번호를 가져온다.
        Integer currentPoliceId = requireLoggedInUserId(jwt);

        // 반려 처리는 경찰 계정만 가능하다.
        requirePolice(jwt);

        // Service가 PENDING 상태 검증, 반려 사유 저장, 처리 시각 저장을 수행한다.
        ReturnRequestProcessResponse response =
                returnRequestService.rejectReturnRequest(
                        currentPoliceId,
                        requestId,
                        request.rejectReason()
                );

        // 처리된 신청 번호, REJECTED 상태, 처리 시각을 200 OK로 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * PATCH /api/v1/return-requests/{requestId}/complete
     * 경찰이 승인된 반환 신청의 실제 물품 인계를 완료한다.
     */
    @Operation(summary = "물품 반환 완료 처리")
    @PatchMapping("/{requestId}/complete")
    public ResponseEntity<ReturnRequestCompleteResponse> completeReturnRequest(
            // Authorization 헤더에서 인증된 경찰 JWT를 받는다.
            @AuthenticationPrincipal Jwt jwt,

            // URL의 반환 신청 번호를 받는다.
            @PathVariable Integer requestId
    ) {
        // JWT의 sub Claim에서 실제 반환 완료를 처리하는 경찰관 번호를 가져온다.
        Integer currentPoliceId = requireLoggedInUserId(jwt);

        // 반환 완료 처리는 경찰 계정만 가능하다.
        requirePolice(jwt);

        // Service에서 승인 상태와 두 물품의 인계 대기 상태를 검증하고 완료 처리한다.
        ReturnRequestCompleteResponse response =
                returnRequestService.completeReturnRequest(
                        currentPoliceId,
                        requestId
                );

        // 완료된 신청 번호와 완료 시각을 200 OK로 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
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

    /**
     * 시민 역할만 허용한다. 시민이 아니면 403 Forbidden을 반환한다.
     */
    private void requireCitizen(Jwt jwt) {
        if (requireLoggedInUserRole(jwt) != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    /**
     * 경찰 역할만 허용한다. 경찰이 아니면 403 Forbidden을 반환한다.
     */
    private void requirePolice(Jwt jwt) {
        if (requireLoggedInUserRole(jwt) != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    /**
     * 반환 신청의 열람 권한이다. 관리자는 감사·현황 확인만 할 수 있으며,
     * 상태를 바꾸는 각 PATCH 메서드는 계속 {@link #requirePolice(Jwt)}를 사용한다.
     */
    private void requirePoliceOrAdmin(Jwt jwt) {
        UserRole role = requireLoggedInUserRole(jwt);
        if (role != UserRole.POLICE && role != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰 또는 관리자 계정만 조회할 수 있는 기능입니다."
            );
        }
    }
}
