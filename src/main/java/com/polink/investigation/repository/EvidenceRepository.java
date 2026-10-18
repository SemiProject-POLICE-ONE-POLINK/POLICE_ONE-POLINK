package com.polink.investigation.repository;

import com.polink.investigation.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, String> {

    // 사건별 증거물 조회 — 등록 시 순번 계산에 사용
    List<Evidence> findByCaseId(String caseId);
}
