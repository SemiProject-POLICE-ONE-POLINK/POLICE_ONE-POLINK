package com.polink.casefile.service;

import com.polink.casefile.repository.CaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.time.ZoneId;
import java.util.Locale;

@Component
public class DefaultCaseNumberGenerator
        implements CaseNumberGenerator {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");
    private static final int MAX_SEQUENCE = 9999;

    private final CaseRepository caseRepository;

    public DefaultCaseNumberGenerator(
            CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Override
    public String generate() {
        int year = Year.now(SEOUL_ZONE).getValue();
        String prefix = "SMPA-" + year + "-";

        int lastSequence = caseRepository
                .findLastCaseIdForUpdate(prefix)
                .map(caseId -> extractSequence(caseId, prefix))
                .orElse(0);

        if (lastSequence >= MAX_SEQUENCE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "해당 연도의 사건번호가 모두 사용되었습니다."
            );
        }

        return String.format(
                Locale.ROOT,
                "%s%04d",
                prefix,
                lastSequence + 1
        );
    }

    private int extractSequence(
            String caseId,
            String prefix) {

        String sequence = caseId.substring(prefix.length());

        if (sequence.length() != 4
                || !sequence.chars().allMatch(Character::isDigit)) {
            throw new IllegalStateException(
                    "잘못된 사건번호 형식입니다: " + caseId
            );
        }

        return Integer.parseInt(sequence);
    }
}