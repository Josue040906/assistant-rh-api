package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.GradeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GradeServiceTests {

    private final GradeRepository repository = mock(GradeRepository.class);
    private final GradeService service = new GradeService(repository);

    @Test
    void refuseLesDatesDeValiditeFutures() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createGrade(
                        "G1", "Grade", null, null,
                        LocalDate.now().plusDays(1), null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createGrade(
                        "G2", "Grade", null, null,
                        null, LocalDate.now().plusDays(1)
                )
        );

        verify(repository, never()).create(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
