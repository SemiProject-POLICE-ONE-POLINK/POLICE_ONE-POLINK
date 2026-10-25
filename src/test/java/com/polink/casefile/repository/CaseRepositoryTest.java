package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.user.entity.User;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CaseRepositoryTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void searchCases_appliesKeywordTypeAndStatus() {
        User reporter = userRepository.save(
                new User(
                        "citizen01",
                        "test-hash",
                        "테스트시민",
                        "010-0000-9901"
                )
        );

        LocalDateTime occurredAt =
                LocalDateTime.of(2026, 10, 25, 10, 0);

        CaseFile matchedCase = new CaseFile(
                "SMPA-2026-0001",
                reporter,
                "서울역 지갑 절도",
                CaseType.THEFT,
                "사건 내용",
                occurredAt,
                "서울역"
        );

        CaseFile wrongTypeCase = new CaseFile(
                "SMPA-2026-0002",
                reporter,
                "서울 금융 사기",
                CaseType.FRAUD,
                "사건 내용",
                occurredAt,
                "서울시청"
        );

        CaseFile wrongStatusCase = new CaseFile(
                "SMPA-2026-0003",
                reporter,
                "서울 지갑 절도",
                CaseType.THEFT,
                "사건 내용",
                occurredAt,
                "서울광장"
        );
        wrongStatusCase.finishInvestigation();

        caseRepository.saveAll(
                List.of(
                        matchedCase,
                        wrongTypeCase,
                        wrongStatusCase
                )
        );
        caseRepository.flush();

        Page<CaseFile> filteredResult =
                caseRepository.searchCases(
                        "서울",
                        CaseType.THEFT,
                        CaseStatus.WAITING,
                        PageRequest.of(0, 10)
                );

        assertEquals(1, filteredResult.getTotalElements());
        assertEquals(
                "SMPA-2026-0001",
                filteredResult.getContent().get(0).getCaseId()
        );

        Page<CaseFile> allResult =
                caseRepository.searchCases(
                        null,
                        null,
                        null,
                        PageRequest.of(0, 10)
                );

        assertEquals(3, allResult.getTotalElements());
    }
}
