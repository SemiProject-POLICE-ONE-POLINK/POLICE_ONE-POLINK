package com.polink.investigation.repository;

import com.polink.investigation.entity.Hypothesis;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HypothesisRepository extends JpaRepository<Hypothesis, Integer> {

    // 해당 사건에서 채택된 가설을 최신 채택순으로 조회
    @Query("""
            SELECT h
            FROM Hypothesis h
            WHERE h.caseFile.caseId = :caseId
            ORDER BY h.adoptedAt DESC, h.hypothesisId DESC
            """)
    List<Hypothesis> findAdoptedByCaseId(@Param("caseId") String caseId);

    // 체크 상태 변경 시 같은 가설의 다른 변경 요청은 대기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h
            FROM Hypothesis h
            WHERE h.caseFile.caseId = :caseId
              AND h.hypothesisId = :hypothesisId
            """)
    Optional<Hypothesis> findByCaseIdAndHypothesisIdForUpdate(
            @Param("caseId") String caseId,
            @Param("hypothesisId") Integer hypothesisId
    );
}
