package bj.asin.registration.controller;

import bj.asin.registration.support.ApiTestSupport;
import bj.asin.registration.support.FakeIdentityGateway.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrantApiTest extends ApiTestSupport {

    @Test
    void listsRegistrantsWithTheirNamesOrderedByTableNumber() {
        post(body("1000000001", "DOUANES", "P"));
        identityGateway.setMode(Mode.UNAVAILABLE);
        post(body("1000000002", "DOUANES", "P"));
        post(body("1000000003", "IMPOTS", "P"));

        HttpResponse<String> response = get("/api/centres/P/inscrits?concours=DOUANES");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
            .contains("{\"numeroTable\":\"P-0001\",\"npi\":\"1000000001\",\"nom\":\"DOSSOU\",\"prenoms\":\"Afi\",\"statut\":\"validee\"}")
            .contains("{\"numeroTable\":\"P-0002\",\"npi\":\"1000000002\",\"nom\":null,\"prenoms\":null,\"statut\":\"en_attente_verification\"}")
            .doesNotContain("1000000003");
    }

    @Test
    void listsFiveThousandRegistrantsInLessThanTwoSeconds() {
        jdbc.execute("""
            INSERT INTO candidats (npi, nom, prenoms, date_naissance, cree_le)
            SELECT (5000000000 + n)::text, 'NOM' || n, 'Prenom', DATE '1995-01-01', now()
            FROM generate_series(1, 5000) n""");
        jdbc.execute("""
            INSERT INTO inscriptions (concours_id, centre_id, npi, numero_table, statut, cree_le)
            SELECT 1, 1, (5000000000 + n)::text, 'C-' || lpad(n::text, 4, '0'), 'validee', now()
            FROM generate_series(1, 5000) n""");
        jdbc.execute("ANALYZE");

        long start = System.nanoTime();
        HttpResponse<String> first = get("/api/centres/C/inscrits?concours=DOUANES&page=0&size=100");
        HttpResponse<String> last = get("/api/centres/C/inscrits?concours=DOUANES&page=49&size=100");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(first.body()).contains("C-0001").contains("C-0100").doesNotContain("C-0101");
        assertThat(last.body()).contains("C-4901").contains("C-5000");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void paginatesTheList() {
        post(body("1000000001", "DOUANES", "P"));
        post(body("1000000002", "DOUANES", "P"));

        assertThat(get("/api/centres/P/inscrits?concours=DOUANES&page=1&size=1").body())
            .contains("P-0002").doesNotContain("P-0001");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/centres/1/inscrits?concours=DOUANES",
        "/api/centres/P/inscrits?concours=INCONNU",
        "/api/centres/P/inscrits?concours=DOUANES&size=500",
        "/api/centres/P/inscrits"
    })
    void rejectsInvalidListParameters(String path) {
        assertThat(get(path).statusCode()).isEqualTo(400);
    }

    @Test
    void searchesByTheStartOfTheLastName() {
        insertCandidate("1000000001", "DOSSOU");
        insertCandidate("1000000002", "DOSSA");
        insertCandidate("1000000003", "AGOSSOU");

        HttpResponse<String> response = get("/api/inscrits/recherche?nom=dos");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("DOSSA", "DOSSOU").doesNotContain("AGOSSOU");
    }

    @Test
    void returnsAtMostFiftyResults() {
        for (int i = 0; i < 60; i++) {
            insertCandidate("20000000%02d".formatted(i), "ZINSOU");
        }

        assertThat(get("/api/inscrits/recherche?nom=ZINSOU").body().split("\"npi\"")).hasSize(51);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'", "' OR '1'='1", "' UNION SELECT npi, nom, date_naissance FROM candidats --", "%", "DOS%"})
    void rejectsTheSec001InjectionAttempts(String name) {
        insertCandidate("1000000001", "DOSSOU");

        HttpResponse<String> response = get("/api/inscrits/recherche?nom=" + URLEncoder.encode(name, StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("DOSSOU").doesNotContainIgnoringCase("sql");
    }

    private void insertCandidate(String npi, String lastName) {
        jdbc.update("INSERT INTO candidats (npi, nom, prenoms, date_naissance, cree_le) VALUES (?, ?, 'Afi', DATE '1995-01-01', now())",
            npi, lastName);
    }
}
