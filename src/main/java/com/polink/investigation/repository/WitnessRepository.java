package com.polink.investigation.repository;

import com.polink.investigation.entity.Witness;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WitnessRepository extends JpaRepository<Witness, String> {

    // 같은 사건의 목격자 목록 조회
    List<Witness> findByCaseFile_CaseId(String caseId);
}
