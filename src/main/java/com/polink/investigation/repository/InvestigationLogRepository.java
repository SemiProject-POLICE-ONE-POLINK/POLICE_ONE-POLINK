package com.polink.investigation.repository;

import com.polink.investigation.entity.InvestigationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationLogRepository extends JpaRepository<InvestigationLog, Integer> {

    // 사건별 수사기록을 수사 일시·기록 번호 오름차순으로 조회
    List<InvestigationLog> findByCaseIdOrderByInvestigatedAtAscLogIdAsc(String caseId);
}
