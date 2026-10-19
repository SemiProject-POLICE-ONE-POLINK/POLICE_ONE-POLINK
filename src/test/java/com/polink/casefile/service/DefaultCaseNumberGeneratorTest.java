package com.polink.casefile.service;

import com.polink.casefile.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultCaseNumberGeneratorTest {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    @Mock
    private CaseRepository caseRepository;

    private DefaultCaseNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DefaultCaseNumberGenerator(
                caseRepository);
    }

    @Test
    void generate_firstCaseOfYear() {
        String prefix = currentPrefix();

        when(caseRepository.findLastCaseIdForUpdate(prefix))
                .thenReturn(Optional.empty());

        String caseId = generator.generate();

        assertEquals(prefix + "0001", caseId);
    }

    @Test
    void generate_nextCaseNumber() {
        String prefix = currentPrefix();

        when(caseRepository.findLastCaseIdForUpdate(prefix))
                .thenReturn(Optional.of(prefix + "0431"));

        String caseId = generator.generate();

        assertEquals(prefix + "0432", caseId);
    }

    @Test
    void generate_overMaximumSequence() {
        String prefix = currentPrefix();

        when(caseRepository.findLastCaseIdForUpdate(prefix))
                .thenReturn(Optional.of(prefix + "9999"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                generator::generate
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
    }

    @Test
    void generate_invalidStoredCaseId() {
        String prefix = currentPrefix();

        when(caseRepository.findLastCaseIdForUpdate(prefix))
                .thenReturn(Optional.of(prefix + "ABCD"));

        assertThrows(
                IllegalStateException.class,
                generator::generate
        );
    }

    private String currentPrefix() {
        int year = Year.now(SEOUL_ZONE).getValue();
        return "SMPA-" + year + "-";
    }
}
