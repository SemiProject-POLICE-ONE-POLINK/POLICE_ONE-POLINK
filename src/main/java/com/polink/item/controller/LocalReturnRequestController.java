package com.polink.item.controller;

import com.polink.item.returnrequest.dev.LocalCurrentUserProvider;
import com.polink.item.returnrequest.dto.MyReturnRequestListResponse;
import com.polink.item.returnrequest.dto.PoliceReturnRequestListResponse;
import com.polink.item.returnrequest.dto.ReturnRequestCreateRequest;
import com.polink.item.returnrequest.dto.ReturnRequestCreateResponse;
import com.polink.item.returnrequest.dto.ReturnRequestDetailResponse;
import com.polink.item.returnrequest.dto.ReturnRequestRejectRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.service.ReturnRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/return-requests")
@RequiredArgsConstructor
public class LocalReturnRequestController {

    private final ReturnRequestService returnRequestService;
    private final LocalCurrentUserProvider currentUserProvider;

    // 시민이 본인의 분실물과 습득물을 연결해 반환을 신청한다.
    @PostMapping
    public ResponseEntity<ReturnRequestCreateResponse> createReturnRequest(
            @Valid @RequestBody ReturnRequestCreateRequest request
    ) {
        currentUserProvider.requireCitizen();

        ReturnRequestCreateResponse response =
                returnRequestService.createReturnRequest(
                        currentUserProvider.getCurrentUserId(),
                        request
                );

        return ResponseEntity
                .created(URI.create("/api/v1/return-requests/" + response.requestId()))
                .body(response);
    }

    // 로그인한 시민이 본인의 반환 신청 목록을 조회한다.
    @GetMapping("/me")
    public ResponseEntity<MyReturnRequestListResponse> getMyReturnRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        currentUserProvider.requireCitizen();

        return ResponseEntity.ok(
                returnRequestService.getMyReturnRequests(
                        currentUserProvider.getCurrentUserId(),
                        page,
                        size
                )
        );
    }

    // 경찰관이 반환 신청 목록을 상태와 검색어로 조회한다.
    @GetMapping
    public ResponseEntity<PoliceReturnRequestListResponse> getReturnRequests(
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        currentUserProvider.requirePolice();

        return ResponseEntity.ok(
                returnRequestService.getReturnRequests(status, keyword, page, size)
        );
    }

    // 경찰관이 반환 신청 한 건의 상세 정보를 조회한다.
    @GetMapping("/{requestId}")
    public ResponseEntity<ReturnRequestDetailResponse> getReturnRequestDetail(
            @PathVariable Integer requestId
    ) {
        currentUserProvider.requirePolice();

        return ResponseEntity.ok(
                returnRequestService.getReturnRequestDetail(requestId)
        );
    }

    // 경찰관이 대기 중인 반환 신청을 승인한다.
    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<Void> approveReturnRequest(
            @PathVariable Integer requestId
    ) {
        currentUserProvider.requirePolice();

        returnRequestService.approveReturnRequest(
                currentUserProvider.getCurrentUserId(),
                requestId
        );

        return ResponseEntity.noContent().build();
    }

    // 경찰관이 대기 중인 반환 신청을 반려한다.
    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<Void> rejectReturnRequest(
            @PathVariable Integer requestId,
            @Valid @RequestBody ReturnRequestRejectRequest request
    ) {
        currentUserProvider.requirePolice();

        returnRequestService.rejectReturnRequest(
                currentUserProvider.getCurrentUserId(),
                requestId,
                request.rejectReason()
        );

        return ResponseEntity.noContent().build();
    }

    // 경찰관이 승인된 반환 신청의 물품 인계를 완료 처리한다.
    @PatchMapping("/{requestId}/complete")
    public ResponseEntity<Void> completeReturnRequest(
            @PathVariable Integer requestId
    ) {
        currentUserProvider.requirePolice();

        returnRequestService.completeReturnRequest(requestId);

        return ResponseEntity.noContent().build();
    }
}
