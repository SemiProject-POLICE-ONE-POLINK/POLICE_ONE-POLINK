package com.polink.investigation.service;

import com.polink.investigation.dto.HypothesisChecklist;
import com.polink.investigation.dto.HypothesisChecklist.CheckItem;
import com.polink.investigation.dto.request.HypothesisChecklistUpdateRequest;
import com.polink.investigation.entity.Hypothesis;
import com.polink.investigation.repository.HypothesisRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class HypothesisChecklistService {

    private final HypothesisRepository hypothesisRepository;
    private final InvestigationAccessService investigationAccessService;
    private final HypothesisChecklistConverter checklistConverter;

    public HypothesisChecklistService(HypothesisRepository hypothesisRepository, InvestigationAccessService investigationAccessService, HypothesisChecklistConverter checklistConverter) {
        this.hypothesisRepository = hypothesisRepository;
        this.investigationAccessService = investigationAccessService;
        this.checklistConverter = checklistConverter;
    }

    // 담당 경찰이 채택된 가설의 체크리스트 조회
    @Transactional(readOnly = true)
    public HypothesisChecklist findChecklist(
            String caseId,
            Integer hypothesisId,
            Integer loggedInUserId
    ) {

        // 1. 해당 사건의 담당 경찰인지 확인
        investigationAccessService.findAssignedCase(
                caseId,
                loggedInUserId
        );

        // 2. 선택한 사건에 속한 가설인지 확인
        Hypothesis hypothesis = hypothesisRepository
                .findById(hypothesisId)
                .filter(saved -> caseId.equals(
                        saved.getCaseFile().getCaseId()
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "선택한 수사 가설을 찾을 수 없습니다."
                ));

        // 3. 저장된 내용과 체크 상태를 객체로 변환해 반환
        return checklistConverter.read(
                hypothesis.getAdditionalRecommendation()
        );
    }

    // 담당 경찰이 추가 확인 항목을 체크하거나 체크 해제
    @Transactional
    public HypothesisChecklist updateCheckItem(
            String caseId,
            Integer hypothesisId,
            Integer itemId,
            Integer loggedInUserId,
            HypothesisChecklistUpdateRequest request
    ) {

        // 1. 해당 사건의 담당 경찰·수사중 상태 확인
        investigationAccessService.findInvestigatingCase(
                caseId,
                loggedInUserId
        );

        // 2. 변경할 항목 번호와 체크 상태 확인
        if (itemId == null
                || itemId < 1
                || request == null
                || request.completed() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "변경할 항목과 체크 상태를 확인해주세요."
            );
        }

        // 3. 가설을 잠금 조회해 다른 요청의 변경이 덮어써지지 않도록 처리
        Hypothesis hypothesis = hypothesisRepository
                .findByCaseIdAndHypothesisIdForUpdate(
                        caseId,
                        hypothesisId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "선택한 수사 가설을 찾을 수 없습니다."
                ));

        // 4. 현재 저장된 체크리스트 복원
        HypothesisChecklist checklist = checklistConverter.read(
                hypothesis.getAdditionalRecommendation()
        );

        List<CheckItem> updatedItems = new ArrayList<>();
        boolean found = false;

        // 5. 선택한 항목의 체크 상태만 변경
        for (CheckItem checkItem : checklist.checkItems()) {

            if (checkItem.itemId().equals(itemId)) {
                updatedItems.add(
                        new CheckItem(
                                checkItem.itemId(),
                                checkItem.content(),
                                request.completed()
                        )
                );

                found = true;

            } else {
                // 다른 항목의 내용과 체크 상태는 유지
                updatedItems.add(checkItem);
            }
        }

        // 선택한 번호의 항목이 없으면 저장하지 않음
        if (!found) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "선택한 확인 항목을 찾을 수 없습니다."
            );
        }

        // 6. 원문과 변경된 항목 목록을 다시 구성
        HypothesisChecklist updatedChecklist =
                new HypothesisChecklist(
                        checklist.recommendation(),
                        updatedItems
                );

        // 7. JSON 문자열로 변환해 기존 필드에 반영
        hypothesis.updateChecklist(
                checklistConverter.write(updatedChecklist)
        );

        // 트랜잭션이 정상 종료되면 JPA가 변경된 값을 DB에 반영
        return updatedChecklist;
    }
}