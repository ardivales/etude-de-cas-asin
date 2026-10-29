package bj.asin.registration.controller;

import bj.asin.registration.config.properties.IdentityClientProperties;
import bj.asin.registration.support.ApiTestSupport;
import bj.asin.registration.support.FakeIdentityGateway.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationApiTest extends ApiTestSupport {

    @Autowired
    private IdentityClientProperties identityProperties;

    @Test
    void registersACandidate() {
        HttpResponse<String> response = post(body("1234567890", "DOUANES", "C"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"numeroTable\":\"C-0001\"", "\"statut\":\"validee\"");
        assertThat(jdbc.queryForObject("SELECT nom FROM candidats WHERE npi = '1234567890'", String.class))
            .isEqualTo("DOSSOU");
    }

    @Test
    void storesInstantsInUtc() {
        post(body("1234567890", "DOUANES", "C"));

        assertThat(jdbc.queryForObject("SELECT to_char(cree_le, 'YYYY-MM-DD HH24:MI:SS') FROM inscriptions", String.class))
            .isEqualTo("2026-11-20 12:00:00");
    }

    @Test
    void assignsSequentialTableNumbers() {
        assertThat(post(body("1000000001", "DOUANES", "P")).body()).contains("P-0001");
        assertThat(post(body("1000000002", "IMPOTS", "P")).body()).contains("P-0002");
        assertThat(post(body("1000000003", "TRESOR", "P")).body()).contains("P-0003");
    }

    @Test
    void rejectsADuplicateRegistration() {
        post(body("1234567890", "DOUANES", "C"));

        HttpResponse<String> response = post(body("1234567890", "DOUANES", "C"));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("déjà inscrit à ce concours");
    }

    @Test
    void rejectsAnExamOnTheSameDay() {
        post(body("1234567890", "DOUANES", "C"));

        HttpResponse<String> response = post(body("1234567890", "IMPOTS", "C"));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("même jour");
    }

    @Test
    void acceptsAnExamOnAnotherDay() {
        post(body("1234567890", "DOUANES", "C"));

        assertThat(post(body("1234567890", "TRESOR", "C")).statusCode()).isEqualTo(201);
    }

    @Test
    void rejectsWhenTheCenterIsFull() {
        jdbc.update("UPDATE centres SET capacite = 1 WHERE code = 'L'");
        post(body("1000000001", "DOUANES", "L"));

        HttpResponse<String> response = post(body("1000000002", "DOUANES", "L"));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("complet");
    }

    @Test
    void rejectsAnUnknownNpiWithoutWritingAnything() {
        identityGateway.setMode(Mode.UNKNOWN);

        HttpResponse<String> response = post(body("1234567890", "DOUANES", "C"));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("NPI inconnu");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscriptions", Integer.class)).isZero();
    }

    @Test
    void registersAsPendingWhenTheIdentityServiceIsUnavailable() {
        identityGateway.setMode(Mode.UNAVAILABLE);

        HttpResponse<String> response = post(body("1234567890", "DOUANES", "C"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"statut\":\"en_attente_verification\"");
    }

    @Test
    void identityTimeoutIsFiveSeconds() {
        assertThat(identityProperties.getTotalTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @ParameterizedTest
    @CsvSource({"1991-06-20, 201", "1992-01-01, 201", "1990-12-31, 409"})
    void appliesTheAgeLimitOnDecember31(String birthDate, int expectedStatus) {
        assertThat(post(body("1234567890", "DOUANES", "C", birthDate)).statusCode()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-20T22:30:00Z, 201", "2026-11-20T22:59:00Z, 201", "2026-11-20T23:00:00Z, 409"})
    void closesTresorAt2359BeninTime(String utcTime, int expectedStatus) {
        clock.set(Instant.parse(utcTime));

        assertThat(post(body("1234567890", "TRESOR", "C")).statusCode()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource({
        "12345, DOUANES, C",
        "1' OR '1'='1, DOUANES, C",
        "1234567890, INCONNU, C",
        "1234567890, DOUANES, Z"
    })
    void rejectsInvalidInput(String npi, String exam, String center) {
        HttpResponse<String> response = post(body(npi, exam, center));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContainIgnoringCase("sql").doesNotContain("Exception");
    }

    @Test
    void rejectsAnUnreadableBody() {
        assertThat(post("{\"npi\": ").statusCode()).isEqualTo(400);
    }
}
