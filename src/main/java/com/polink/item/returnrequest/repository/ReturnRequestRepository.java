package com.polink.item.returnrequest.repository;

import com.polink.item.returnrequest.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 반환 신청(tbl_return_request)을 조회하고 저장하는 Repository다.
 */
public interface ReturnRequestRepository
        extends JpaRepository<ReturnRequest, Integer> {

    /*
     * 같은 분실물과 습득물 조합으로 반환 신청이 이미 있는지 확인한다.
     * 중복 반환 신청을 막는 데 사용한다.
     */
    boolean existsByLostItemIdAndFoundItemId(
            String lostItemId,
            String foundItemId
    );

    /*
     * 특정 시민이 신청한 반환 내역을 최신 신청순으로 페이지 조회한다.
     * 로그인 연동 후 인증된 사용자 번호(requesterId)를 전달받는다.
     */
    Page<ReturnRequest> findByRequesterIdOrderByCreatedAtDesc(
            Integer requesterId,
            Pageable pageable
    );

    /*
     * 경찰관이 반환 신청을 상태와 검색어로 조회한다.
     * status와 keyword가 null이면 전체 반환 신청을 최신순으로 조회한다.
     * keyword는 분실물 번호, 습득물 번호, 신청 사유에서 검색한다.
     */
    @Query(
            value = """
                select returnRequest
                from ReturnRequest returnRequest
                where (:status is null or returnRequest.status = :status)
                  and (:keyword is null
                       or lower(returnRequest.lostItemId)
                            like concat('%', lower(:keyword), '%')
                       or lower(returnRequest.foundItemId)
                            like concat('%', lower(:keyword), '%')
                       or lower(returnRequest.requestReason)
                            like concat('%', lower(:keyword), '%'))
                order by returnRequest.createdAt desc,
                         returnRequest.requestId desc
                """,
            countQuery = """
                select count(returnRequest)
                from ReturnRequest returnRequest
                where (:status is null or returnRequest.status = :status)
                  and (:keyword is null
                       or lower(returnRequest.lostItemId)
                            like concat('%', lower(:keyword), '%')
                       or lower(returnRequest.foundItemId)
                            like concat('%', lower(:keyword), '%')
                       or lower(returnRequest.requestReason)
                            like concat('%', lower(:keyword), '%'))
                """
    )
    Page<ReturnRequest> findReturnRequestsByConditions(
            @Param("status") ReturnRequestStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}