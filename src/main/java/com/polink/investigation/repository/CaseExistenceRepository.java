package com.polink.investigation.repository;

import com.polink.investigation.dto.response.BoardCaseResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // 수사보드용 사건 기본 정보 조회 — 사건이 없으면 빈 Optional 반환
    public Optional<BoardCaseResponse> findBoardCase(String caseId) {

        // 1. 사건번호로 수사보드에 필요한 컬럼만 조회
        String query = "SELECT case_id, title, case_type, status, " +
                "occurred_at, location, description, assigned_officer_id " +
                "FROM tbl_case " +
                "WHERE case_id = ?";

        Query nativeQuery = entityManager.createNativeQuery(query)
                .setParameter(1, caseId);

        // 2. 결과가 없어도 예외가 나지 않도록 목록으로 조회
        List<?> rows = nativeQuery.getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        // 3. 한 행의 값은 SELECT에 적은 컬럼 순서대로 배열에 담겨 있음
        Object[] row = (Object[]) rows.get(0);

        Number officerId = (Number) row[7]; // 미배정이면 null

        return Optional.of(new BoardCaseResponse(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                toLocalDateTime(row[4]),
                (String) row[5],
                (String) row[6],
                officerId == null ? null : officerId.intValue()
        ));
    }

    // DB 날짜 값을 LocalDateTime으로 변환 (드라이버에 따라 반환 타입이 다를 수 있음)
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }
}
