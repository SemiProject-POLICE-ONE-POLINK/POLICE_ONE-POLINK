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
import com.polink.item.returnrequest.dto.ReturnRequestCompleteResponse;
import com.polink.item.returnrequest.dto.ReturnRequestProcessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    /*
     * 경찰관이 대기 중인 반환 신청을 승인한다.
     * 실제 경찰 권한 확인은 로그인 기능 연동 후 Controller에서 처리한다.
     */
    @Transactional
    public ReturnRequestProcessResponse approveReturnRequest(
            Integer policeId,
            Integer requestId
    ) {
        if (policeId == null || policeId < 1) {
            throw new IllegalArgumentException("처리 경찰관 정보가 필요합니다.");
        }

        if (requestId == null || requestId < 1) {
            throw new IllegalArgumentException("반환 신청 번호는 1 이상이어야 합니다.");
        }

        ReturnRequest returnRequest = returnRequestRepository.findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("반환 신청을 찾을 수 없습니다."));

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new IllegalArgumentException("대기 상태의 반환 신청만 승인할 수 있습니다.");
        }

        Item lostItem = itemRepository.findById(returnRequest.getLostItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("연결된 분실물을 찾을 수 없습니다."));

        Item foundItem = itemRepository.findById(returnRequest.getFoundItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("연결된 습득물을 찾을 수 없습니다."));

        if (lostItem.getItemType() != ItemType.LOST
                || lostItem.getStatus() != ItemStatus.LOST) {
            throw new IllegalArgumentException("승인 가능한 상태의 분실물이 아닙니다.");
        }

        if (foundItem.getItemType() != ItemType.FOUND
                || foundItem.getStatus() != ItemStatus.STORED) {
            throw new IllegalArgumentException("승인 가능한 상태의 습득물이 아닙니다.");
        }

        // 반환 신청 엔티티에 승인한 경찰관, 승인 상태, 처리 시각을 기록한다.
        returnRequest.approve(policeId);

        // 승인되면 두 물품은 실제 인계를 기다리는 상태가 된다.
        lostItem.changeStatus(ItemStatus.HANDOVER_PENDING);
        foundItem.changeStatus(ItemStatus.HANDOVER_PENDING);

        // 변경된 승인 결과를 200 OK 응답 본문으로 돌려준다.
        return ReturnRequestProcessResponse.from(returnRequest);
    }

    /*
     * 경찰관이 대기 중인 반환 신청을 반려한다.
     * 반려해도 분실물·습득물 상태는 바꾸지 않는다.
     * 반환 신청의 상태, 처리 경찰관, 반려 사유, 처리 시각만 변경한다.
     */
    @Transactional
    public ReturnRequestProcessResponse rejectReturnRequest(
            Integer policeId,
            Integer requestId,
            String rejectReason
    ) {
        // 처리한 경찰관 정보가 없거나 잘못됐으면 진행하지 않는다.
        if (policeId == null || policeId < 1) {
            throw new IllegalArgumentException("처리 경찰관 정보가 필요합니다.");
        }

        // 반환 신청 번호는 양수여야 한다.
        if (requestId == null || requestId < 1) {
            throw new IllegalArgumentException("반환 신청 번호는 1 이상이어야 합니다.");
        }

        // 빈 반려 사유는 저장하지 않는다.
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }

        // 먼저 DB에서 반환 신청을 조회해야 returnRequest를 사용할 수 있다.
        ReturnRequest returnRequest = returnRequestRepository.findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("반환 신청을 찾을 수 없습니다."));

        // 아직 처리되지 않은 PENDING 상태만 반려할 수 있다.
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new IllegalArgumentException("대기 상태의 반환 신청만 반려할 수 있습니다.");
        }

        // 앞뒤 공백을 없앤 반려 사유와 처리 정보를 엔티티에 저장한다.
        returnRequest.reject(policeId, rejectReason.trim());

        // 변경된 신청 정보를 200 OK 응답 본문으로 반환한다.
        return ReturnRequestProcessResponse.from(returnRequest);
    }

    /*
     * 승인된 반환 신청의 실제 물품 인계를 완료한다.
     * 완료되면 신청과 연결된 두 물품을 모두 반환 완료 상태로 변경하고,
     * 완료 시각을 API 응답으로 전달한다.
     */
    @Transactional
    public ReturnRequestCompleteResponse completeReturnRequest(Integer policeId, Integer requestId) {
        // 실제 반환 완료를 처리하는 경찰관 정보가 필요하다.
        if (policeId == null || policeId < 1) {
            throw new IllegalArgumentException("처리 경찰관 정보가 필요합니다.");
        }

        if (requestId == null || requestId < 1) {
            throw new IllegalArgumentException("반환 신청 번호는 1 이상이어야 합니다.");
        }

        ReturnRequest returnRequest = returnRequestRepository.findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("반환 신청을 찾을 수 없습니다."));

        // 승인된 신청만 실제 인계 완료 처리할 수 있다.
        if (returnRequest.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new IllegalArgumentException("승인된 반환 신청만 인계 완료 처리할 수 있습니다.");
        }

        Item lostItem = itemRepository.findById(returnRequest.getLostItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("연결된 분실물을 찾을 수 없습니다."));

        Item foundItem = itemRepository.findById(returnRequest.getFoundItemId())
                .orElseThrow(() ->
                        new IllegalArgumentException("연결된 습득물을 찾을 수 없습니다."));

        // 승인 후 두 물품 모두 인계 대기 상태여야 한다.
        if (lostItem.getItemType() != ItemType.LOST
                || lostItem.getStatus() != ItemStatus.HANDOVER_PENDING) {
            throw new IllegalArgumentException("인계 완료 가능한 상태의 분실물이 아닙니다.");
        }

        if (foundItem.getItemType() != ItemType.FOUND
                || foundItem.getStatus() != ItemStatus.HANDOVER_PENDING) {
            throw new IllegalArgumentException("인계 완료 가능한 상태의 습득물이 아닙니다.");
        }

        // 승인·반려 처리 이력은 유지하고,
        // 실제 인계 완료 담당자와 완료 시각을 별도로 저장한다.
        returnRequest.complete(policeId);

        // 연결된 분실물과 습득물도 실제 반환 완료 상태로 변경한다.
        lostItem.completeReturn();
        foundItem.completeReturn();

        // 신청 엔티티에 기록된 완료 시각을 응답으로 반환한다.
        return new ReturnRequestCompleteResponse(
                    returnRequest.getRequestId(),
                    returnRequest.getCompletedAt()
        );
    }

    /*
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
                .map(this::toListResponse)
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
                .map(this::toListResponse)
                .toList();

        return new PoliceReturnRequestListResponse(items);
    }

    /*
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

        return toDetailResponse(returnRequest);
    }

    /*
     * 반환 신청 목록 한 건에 연결된 분실물·습득물 정보를 넣어
     * 목록 응답 DTO로 변환한다.
     */
    private ReturnRequestListResponse toListResponse(
            ReturnRequest returnRequest
    ) {
        // 반환 신청에 연결된 분실물 정보를 조회한다.
        Item lostItem = findLinkedItem(
                returnRequest.getLostItemId(),
                "연결된 분실물을 찾을 수 없습니다."
        );

        // 반환 신청에 연결된 습득물 정보를 조회한다.
        Item foundItem = findLinkedItem(
                returnRequest.getFoundItemId(),
                "연결된 습득물을 찾을 수 없습니다."
        );

        // 신청 정보와 두 물품 정보를 목록 응답으로 합쳐 반환한다.
        return ReturnRequestListResponse.from(
                returnRequest,
                lostItem,
                foundItem
        );
    }

    /*
     * 반환 신청 상세 한 건에 연결된 분실물·습득물 정보를 넣어
     * 상세 응답 DTO로 변환한다.
     */
    private ReturnRequestDetailResponse toDetailResponse(
            ReturnRequest returnRequest
    ) {
        // 반환 신청에 연결된 분실물 상세 정보를 조회한다.
        Item lostItem = findLinkedItem(
                returnRequest.getLostItemId(),
                "연결된 분실물을 찾을 수 없습니다."
        );

        // 반환 신청에 연결된 습득물 상세 정보를 조회한다.
        Item foundItem = findLinkedItem(
                returnRequest.getFoundItemId(),
                "연결된 습득물을 찾을 수 없습니다."
        );

        // 신청 정보와 두 물품의 상세 정보를 하나의 DTO로 합쳐 반환한다.
        return ReturnRequestDetailResponse.from(
                returnRequest,
                lostItem,
                foundItem
        );
    }

    /*
     * 반환 신청에 연결된 물품을 공통으로 조회한다.
     * 목록·상세 조회에서 같은 물품 조회 코드를 반복하지 않도록 분리했다.
     */
    private Item findLinkedItem(
            String itemId,
            String notFoundMessage
    ) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException(notFoundMessage));
    }

    /*
     * 로그인한 시민이 본인이 신청한 반환 신청 한 건만 상세 조회한다.
     * 경찰용 상세 조회와 달리 requesterId를 함께 받아,
     * 다른 시민의 반환 신청 번호를 URL에 직접 넣어도 조회하지 못하게 막는다.
     */
    public ReturnRequestDetailResponse getMyReturnRequestDetail(
            Integer requesterId,
            Integer requestId
    ) {
        // 로그인한 시민의 회원 번호가 올바른지 먼저 확인한다.
        if (requesterId == null || requesterId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }

        // 반환 신청 번호는 DB의 양수 번호만 허용한다.
        if (requestId == null || requestId < 1) {
            throw new IllegalArgumentException("반환 신청 번호는 1 이상이어야 합니다.");
        }

        // 요청한 반환 신청을 DB에서 조회한다.
        ReturnRequest returnRequest = returnRequestRepository
                .findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("반환 신청을 찾을 수 없습니다."));

        // 신청자 번호와 로그인한 시민 번호가 다르면 접근을 금지한다.
        if (!returnRequest.getRequesterId().equals(requesterId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 신청한 반환 신청만 조회할 수 있습니다."
            );
        }

        // 소유권 검증을 통과한 본인 신청만 상세 DTO로 변환해 반환한다.
        return toDetailResponse(returnRequest);
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
