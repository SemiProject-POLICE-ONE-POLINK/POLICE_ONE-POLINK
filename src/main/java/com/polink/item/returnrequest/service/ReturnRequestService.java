package com.polink.item.returnrequest.service;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.dto.ReturnRequestCreateRequest;
import com.polink.item.returnrequest.dto.ReturnRequestCreateResponse;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.polink.item.returnrequest.dto.MyReturnRequestListResponse;
import com.polink.item.returnrequest.dto.ReturnRequestListResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.polink.item.returnrequest.dto.PoliceReturnRequestListResponse;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.dto.ReturnRequestDetailResponse;

/*
 * 반환 신청의 등록과 조회 규칙을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnRequestService {

    private final ReturnRequestRepository returnRequestRepository;
    private final ItemRepository itemRepository;

    /**
     * 시민이 본인의 분실물과 습득물을 연결하여 반환을 신청한다.
     * requesterId는 로그인 기능이 완성되면 인증 정보에서 가져온다.
     */
    @Transactional
    public ReturnRequestCreateResponse createReturnRequest(
            Integer requesterId,
            ReturnRequestCreateRequest request
    ) {
        // 로그인한 시민 정보가 없다면 신청할 수 없다.
        if (requesterId == null || requesterId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }

        // 분실물 번호와 습득물 번호가 같을 수는 없다.
        if (request.lostItemId().equals(request.foundItemId())) {
            throw new IllegalArgumentException("분실물과 습득물 번호는 서로 달라야 합니다.");
        }

        // 요청한 분실물과 습득물이 실제로 존재하는지 확인한다.
        Item lostItem = itemRepository.findById(request.lostItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("분실물을 찾을 수 없습니다."));

        Item foundItem = itemRepository.findById(request.foundItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("습득물을 찾을 수 없습니다."));

        // 신청자는 자신이 등록한 분실물에 대해서만 반환을 요청할 수 있다.
        if (!lostItem.getRegistrantId().equals(requesterId)) {
            throw new IllegalArgumentException("본인이 등록한 분실물만 반환 신청할 수 있습니다.");
        }

        // 첫 번째 물품은 분실물이어야 한다.
        if (lostItem.getItemType() != ItemType.LOST) {
            throw new IllegalArgumentException("분실물 번호에는 LOST 유형의 물품이 필요합니다.");
        }

        // 반환 전 분실물은 LOST 상태여야 한다.
        if (lostItem.getStatus() != ItemStatus.LOST) {
            throw new IllegalArgumentException("반환 가능한 상태의 분실물이 아닙니다.");
        }

        // 두 번째 물품은 습득물이어야 한다.
        if (foundItem.getItemType() != ItemType.FOUND) {
            throw new IllegalArgumentException("습득물 번호에는 FOUND 유형의 물품이 필요합니다.");
        }

        // 반환 전 습득물은 보관 중(STORED) 상태여야 한다.
        if (foundItem.getStatus() != ItemStatus.STORED) {
            throw new IllegalArgumentException("반환 가능한 상태의 습득물이 아닙니다.");
        }

        // 동일한 분실물·습득물 조합은 한 번만 신청할 수 있다.
        if (returnRequestRepository.existsByLostItemIdAndFoundItemId(
                request.lostItemId(),
                request.foundItemId()
        )) {
            throw new IllegalArgumentException("이미 반환 신청한 물품 조합입니다.");
        }

        // 새 반환 신청은 생성될 때 자동으로 PENDING 상태가 된다.
        ReturnRequest returnRequest = new ReturnRequest(
                requesterId,
                request.lostItemId(),
                request.foundItemId(),
                request.requestReason()
        );

        ReturnRequest savedReturnRequest =
                returnRequestRepository.save(returnRequest);

        return ReturnRequestCreateResponse.from(savedReturnRequest);
    }

    /**
     * 로그인한 시민이 신청한 반환 신청 목록을 최신순으로 조회한다.
     * page는 0부터 시작하며, size는 한 페이지의 목록 개수다.
     */
    public MyReturnRequestListResponse getMyReturnRequests(
            Integer requesterId,
            int page,
            int size
    ) {
        // 인증된 로그인 사용자 정보가 필요하다.
        if (requesterId == null || requesterId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }

        // 페이지 번호는 0부터 시작한다.
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        // 한 번에 너무 많은 반환 신청을 조회하지 않도록 제한한다.
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        Pageable pageable = PageRequest.of(page, size);

        // 본인이 신청한 반환 신청만 최신 신청순으로 가져온다.
        Page<ReturnRequest> returnRequestPage = returnRequestRepository
                .findByRequesterIdOrderByCreatedAtDesc(requesterId, pageable);

        List<ReturnRequestListResponse> items = returnRequestPage.getContent()
                .stream()
                .map(ReturnRequestListResponse::from)
                .toList();

        // API 명세대로 items 목록만 응답한다.
        return new MyReturnRequestListResponse(items);
    }

    /*
     * 경찰관이 반환 신청을 상태와 검색어로 조회한다.
     * 실제 경찰 권한 검증은 로그인 기능이 연결된 Controller에서 처리한다.
     */
    public PoliceReturnRequestListResponse getReturnRequests(
            ReturnRequestStatus status,
            String keyword,
            int page,
            int size
    ) {
        // 페이지 번호는 0부터 시작한다.
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        // 한 번에 너무 많은 반환 신청을 조회하지 않도록 제한한다.
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        Pageable pageable = PageRequest.of(page, size);

        // 공백 검색어는 검색 조건 없음(null)으로 처리한다.
        String normalizedKeyword = normalizeKeyword(keyword);

        Page<ReturnRequest> returnRequestPage = returnRequestRepository
                .findReturnRequestsByConditions(
                        status,
                        normalizedKeyword,
                        pageable
                );

        List<ReturnRequestListResponse> items = returnRequestPage.getContent()
                .stream()
                .map(ReturnRequestListResponse::from)
                .toList();

        return new PoliceReturnRequestListResponse(items);
    }

    /**
     * 경찰관이 반환 신청 번호로 한 건을 상세 조회한다.
     * 실제 경찰 권한 검증은 로그인 기능이 연결된 Controller에서 처리한다.
     */
    public ReturnRequestDetailResponse getReturnRequestDetail(
            Integer requestId
    ) {
        // 반환 신청 번호는 DB의 AUTO_INCREMENT 양수 값이다.
        if (requestId == null || requestId < 1) {
            throw new IllegalArgumentException("반환 신청 번호는 1 이상이어야 합니다.");
        }

        ReturnRequest returnRequest = returnRequestRepository
                .findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("반환 신청을 찾을 수 없습니다."));

        return ReturnRequestDetailResponse.from(returnRequest);
    }

    /*
     * null, 빈 문자열, 공백만 있는 검색어는 검색 조건 없음으로 바꾼다.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
