package com.polink.investigation.service;

import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.StatementCreateRequest;
import com.polink.investigation.dto.response.StatementResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class StatementService {

    private final StatementRepository statementRepository;
    private final WitnessRepository witnessRepository;
    private final InvestigationAccessService investigationAccessService;

    public StatementService(StatementRepository statementRepository, WitnessRepository witnessRepository, InvestigationAccessService investigationAccessService) {
        this.statementRepository = statementRepository;
        this.witnessRepository = witnessRepository;
        this.investigationAccessService = investigationAccessService;
    }

    // 기존 목격자에게 새 진술 등록 — 이전 진술은 유지
    @Transactional
    public StatementResponse createStatement(
            String caseId,
            String witnessId,
            Integer loggedInUserId,
            StatementCreateRequest request
    ) {

        // 1. 로그인한 사용자가 수사중인 사건의 담당 경찰인지 확인
        investigationAccessService.findInvestigatingCase(
                caseId,
                loggedInUserId
        );

        // 2. 진술을 남길 목격자가 등록되어 있는지 확인
        Witness witness = witnessRepository.findById(witnessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "진술을 추가하려면 목격자와 최초 진술을 먼저 등록해주세요."
                ));

        // 3. 선택한 사건에 속한 목격자인지 확인
        // (URL에 사건번호와 목격자 번호가 잘못 조합되어 전달되었을 때를 대비)
        // ex) 2026-0405 사건에 2026-0406 사건의 목격자로 진술을 등록 시도 시 중단
        if (!caseId.equals(witness.getCaseFile().getCaseId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "해당 사건에 등록된 목격자를 찾을 수 없습니다."
            );
        }

        // 4. 최초 진술 유형으로 추가 등록을 요청하면 거부
        if (request.statementType() == StatementType.INITIAL) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "최초 진술은 추가 등록할 수 없습니다. 추가·정정·번복 중 선택해주세요."
            );
        }

        // 5. 기존 진술을 수정하지 않고 새로운 진술 객체 생성
        Statement statement = new Statement(
                witness.getWitnessId(),
                request.content(),
                request.statementType(),
                LocalDateTime.now()
        );

        // 6. 새 진술 저장 — 진술 번호는 DB에서 자동 생성
        Statement savedStatement =
                statementRepository.save(statement);

        // 7. 저장된 진술을 응답 DTO로 변환
        return StatementResponse.from(savedStatement);
    }
}
