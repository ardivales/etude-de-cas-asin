package bj.asin.registration.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationRulesTest {

    private static final LocalDate EXAM_DATE_2026 = LocalDate.of(2026, 12, 12);
    private static final Instant TRESOR_CLOSING = Instant.parse("2026-11-20T22:59:00Z");

    @ParameterizedTest
    @CsvSource({
        "1991-06-20, true",
        "1991-01-01, true",
        "1992-01-01, true",
        "1991-12-31, true",
        "1990-12-31, false",
        "1985-02-09, false"
    })
    void ageIsComputedOnDecember31OfTheExamYear(LocalDate birthDate, boolean expected) {
        assertThat(RegistrationRules.isAgeAllowed(birthDate, EXAM_DATE_2026)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-11-20T22:59:05+01:00, true",
        "2026-11-20T23:59:00+01:00, true",
        "2026-11-20T23:59:01+01:00, false",
        "2026-11-21T00:30:00+01:00, false"
    })
    void closingIsComparedInBeninTime(String beninTime, boolean expected) {
        Instant now = OffsetDateTime.parse(beninTime).toInstant();
        assertThat(RegistrationRules.isRegistrationOpen(now, TRESOR_CLOSING)).isEqualTo(expected);
    }
}
