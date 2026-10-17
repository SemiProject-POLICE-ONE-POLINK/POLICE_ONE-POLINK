package com.polink.investigation.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

// 사건 번호 조회를 위한 임시 Repository로 사건 관리자와 병합 시 삭제 예정
@Repository
public class CaseExistenceRepository {
    private final EntityManager entityManager;

    public CaseExistenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // 해당 사건번호로 접수된 사건이 존재하는지 조회
    public boolean existsByCaseId(String caseId) {

        // 1. 사건번호가 일치하는 데이터 개수 조회
        String query = "SELECT COUNT(*) " +
                "FROM tbl_case " +
                "WHERE case_id = ?";

        // 2. 첫 번째 물음표에 사건번호 전달
        Query nativeQuery = entityManager.createNativeQuery(query)
                .setParameter(1, caseId);

        // 3. 조회된 개수 가져오기
        Number count = (Number) nativeQuery.getSingleResult();

        // 4. 데이터가 있으면 true, 없으면 false 반환
        return count.longValue() > 0;
    }
}
