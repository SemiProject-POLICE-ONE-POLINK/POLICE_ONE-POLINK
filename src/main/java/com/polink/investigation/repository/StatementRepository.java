package com.polink.investigation.repository;

import com.polink.investigation.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Integer> {

    // 전달받은 목격자 번호들에 해당하는 진술을 한 번에 조회
    // 등록 일시 오름차순, 일시가 같으면 진술 번호 오름차순
    List<Statement> findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
            List<String> witnessIds
    );
}