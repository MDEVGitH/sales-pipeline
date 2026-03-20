package com.crm.qualifier.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ProspectTest {

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    @Test
    void shouldCreateProspectFromLead() {
        Prospect prospect = Prospect.fromLead(VALID_LEAD, 75);
        assertEquals(VALID_LEAD.nationalId(), prospect.nationalId());
        assertEquals(VALID_LEAD.birthdate(), prospect.birthdate());
        assertEquals(VALID_LEAD.firstName(), prospect.firstName());
        assertEquals(VALID_LEAD.lastName(), prospect.lastName());
        assertEquals(VALID_LEAD.email(), prospect.email());
        assertEquals(75, prospect.qualificationScore());
        assertNotNull(prospect.qualifiedAt());
    }

    @Test
    void shouldReject_whenScoreBelow61() {
        assertThrows(IllegalArgumentException.class, () -> Prospect.fromLead(VALID_LEAD, 60));
    }

    @Test
    void shouldReject_whenScoreAbove100() {
        assertThrows(IllegalArgumentException.class, () -> Prospect.fromLead(VALID_LEAD, 101));
    }

    @Test
    void shouldAcceptBoundaryScore61() {
        Prospect prospect = Prospect.fromLead(VALID_LEAD, 61);
        assertEquals(61, prospect.qualificationScore());
    }

    @Test
    void shouldAcceptBoundaryScore100() {
        Prospect prospect = Prospect.fromLead(VALID_LEAD, 100);
        assertEquals(100, prospect.qualificationScore());
    }

    @Test
    void shouldSetQualifiedAtToNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        Prospect prospect = Prospect.fromLead(VALID_LEAD, 75);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertTrue(prospect.qualifiedAt().isAfter(before) || prospect.qualifiedAt().isEqual(before));
        assertTrue(prospect.qualifiedAt().isBefore(after) || prospect.qualifiedAt().isEqual(after));
    }
}
