package bj.asin.registration.controller;

import bj.asin.registration.support.ApiTestSupport;
import bj.asin.registration.support.FakeIdentityGateway.Mode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureObservability
class ObservabilityTest extends ApiTestSupport {

    @Test
    void healthIsUpWithTheDatabase() {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void openApiDescribesTheRegistrationEndpoint() {
        var response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/inscriptions", "API Inscriptions aux concours");
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusExposesRegistrationsByStatusAndRejectionsByReason() {
        post(body("1234567890", "DOUANES", "C"));
        post(body("1234567890", "DOUANES", "C"));
        identityGateway.setMode(Mode.UNAVAILABLE);
        post(body("1234567891", "DOUANES", "C"));

        String metrics = get("/actuator/prometheus").body();

        assertThat(metrics).contains(
            "inscriptions_enregistrees_total{statut=\"validee\"}",
            "inscriptions_enregistrees_total{statut=\"en_attente_verification\"}",
            "inscriptions_refusees_total{motif=\"DEJA_INSCRIT\"}");
    }
}
