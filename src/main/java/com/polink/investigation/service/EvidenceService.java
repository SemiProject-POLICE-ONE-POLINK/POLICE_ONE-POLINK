package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.dto.response.EvidenceListResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.repository.EvidenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceFileService evidenceFileService;
    private final CaseRepository caseRepository;

    public EvidenceService(EvidenceRepository evidenceRepository, EvidenceFileService evidenceFileService, CaseRepository caseRepository) {
        this.evidenceRepository = evidenceRepository;
        this.evidenceFileService = evidenceFileService;
        this.caseRepository = caseRepository;
    }


    // 첨부 파일이 없는 증거물 등록 (파일 위치에 null 값 전달)
    @Transactional
    public EvidenceCreateResponse createEvidence(
            String caseId,
            EvidenceCreateRequest request
    ) {
        return createEvidence(caseId, request, null);
    }

    // 첨부 파일이 있는 증거물 등록
    @Transactional
    public EvidenceCreateResponse createEvidence(
            String caseId,
            EvidenceCreateRequest request,
            MultipartFile file
    ) {
        // 1. 증거물을 등록할 사건이 존재하는지 확인
        // 없을 시 기존 등록 오류 처리
        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당하는 사건을 찾을 수 없어 증거물을 등록할 수 없습니다."
                ));

        // 2. 해당 사건에 저장된 증거물 목록 조회
        List<Evidence> savedEvidences = evidenceRepository.findByCaseFile_CaseId(caseId);

        // 3. 다음 증거번호 생성
        String evidenceId = generateEvidenceId(caseId, savedEvidences);

        // 4. 요청 정보로 새 증거물 객체 생성
        Evidence evidence = new Evidence(
                evidenceId,
                caseFile,
                request.evidenceName(),
                request.evidenceType(),
                request.description(),
                LocalDateTime.now(),
                request.acquiredLocation()
        );


        // 첨부파일이 있으면 검증·저장하고 경로 설정
        if (file != null) {

            // DB 저장 실패 시 파일을 삭제할 수 있도록 트랜잭션 적용 여부 확인
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException(
                        "증거물 등록 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                );
            }

            String filePath = evidenceFileService.save(file);

            // DB 등록이 취소되면 저장된 파일을 삭제하도록 예약
            registerFileCleanup(filePath);

            evidence.setFilePath(filePath);
        }

        // 5. 증거물 저장
        Evidence savedEvidence = evidenceRepository.save(evidence);

        // 6. 변환 메서드를 호출하여 응답 DTO로 변환
        return EvidenceCreateResponse.from(savedEvidence);
    }

    // 사건별 증거물 목록 조회
    @Transactional(readOnly = true)
    public List<EvidenceListResponse> findEvidenceList(String caseId) {

        // 사건이 없으면 HTTP 404로 처리
        if (!caseRepository.existsById(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "사건 정보를 확인할 수 없어 증거물 목록을 조회할 수 없습니다."
            );
        }

        // 해당 사건의 증거물을 정렬 가능한 목록으로 가져오기
        List<Evidence> evidences = new ArrayList<>(
                evidenceRepository.findByCaseFile_CaseId(caseId)
        );

        // 증거번호 오름차순 정렬: 01 → 02 → 03
        evidences.sort(
                Comparator.comparing(Evidence::getEvidenceId)
        );

        // 조회 결과를 응답 DTO 목록으로 변환
        List<EvidenceListResponse> responses = new ArrayList<>();

        for (Evidence evidence : evidences) {
            responses.add(EvidenceListResponse.from(evidence));
        }

        // 증거물이 없으면 빈 목록 반환
        return responses;
    }

    // DB 등록이 취소되면 저장된 첨부파일 정리
    private void registerFileCleanup(String filePath) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        // DB 등록이 취소된 경우에만 파일 삭제
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                evidenceFileService.delete(filePath);

                            } catch (RuntimeException exception) {
                                log.error(
                                        "DB 저장 롤백 후 첨부 파일 삭제 실패: {}",
                                        filePath,
                                        exception
                                );
                            }

                        } else if (status == STATUS_UNKNOWN) {
                            // 저장 여부가 불확실하면 파일을 유지하고 로그 기록
                            log.error(
                                    "DB 처리 결과 확인 필요. 첨부 파일 유지: {}",
                                    filePath
                            );
                        }
                    }
                }
        );
    }

    // 1-1. 접수된 사건이 없으면 증거물 등록 불가
    private void checkCaseExists(String caseId) {

        if (!caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException(
                    "해당하는 사건을 찾을 수 없어 증거물을 등록할 수 없습니다."
            );
        }
    }

    // 3-1. 같은 사건에 저장된 증거물의 마지막 순번 다음 번호를 생성
    private String generateEvidenceId(
            String caseId,
            List<Evidence> savedEvidences
    ) {
        // (1) 사건번호의 인덱스 5부터 끝까지 가져와 앞의 "SMPA-"를 제외한다.
        // (2) Evidence의 약자인 'EV-'에 가져온 문자열과 '-' 를 붙인다.
        // ex) SMPA-2026-0405 → EV-2026-0405-
        String newPrefix = "EV-" + caseId.substring(5) + "-";

        int lastNumber = 0;

        // 저장된 증거물이 없으면 반복하지 않고 0을 유지
        for (Evidence evidence : savedEvidences) {
            String evidenceId = evidence.getEvidenceId();

            // 증거 번호의 마지막 하이픈 뒤에 있는 순번 추출
            String numberText = evidenceId.substring(
                    evidenceId.lastIndexOf("-") + 1
            );

            // 문자열 순번을 숫자로 변환
            int number = Integer.parseInt(numberText);

            // 저장된 순번 중 가장 큰 값 유지
            lastNumber = Math.max(lastNumber, number);
        }
        // 두 자리 순번의 최대값을 넘으면 등록 중단
        if (lastNumber >= 99) {
            throw new IllegalStateException(
                    "해당 사건의 증거번호는 99번까지 등록할 수 있습니다."
            );
        }

        /* 마지막 순번에 1을 더해 다음 순번 생성 ex) EV-2026-0405-04
         *  %02d: 순번을 두 자리로 표시(1 → 01)
         *  Locale.ROOT: 언어 설정과 관계없이 0~9 사용
         * */
        return newPrefix
                + String.format(Locale.ROOT, "%02d", lastNumber + 1);
    }

    // 파일 정리 실패 등 서버 오류 기록
    private static final Logger log =
            LoggerFactory.getLogger(EvidenceService.class);

}