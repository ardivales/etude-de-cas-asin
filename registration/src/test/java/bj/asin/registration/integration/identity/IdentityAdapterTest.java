package bj.asin.registration.integration.identity;

import bj.asin.registration.config.IdentityClientConfiguration;
import bj.asin.registration.config.properties.IdentityClientProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class IdentityAdapterTest {

    private static ClientAndServer server;
    private IdentityAdapter adapter;

    @BeforeAll
    static void start() {
        server = ClientAndServer.startClientAndServer(0);
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.reset();
        IdentityClientProperties properties = new IdentityClientProperties();
        properties.setBaseUrl("http://localhost:" + server.getPort());
        properties.setIdentityPath("/identite/{npi}");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setTotalTimeout(Duration.ofMillis(500));
        WebClient webClient = new IdentityClientConfiguration().identityWebClient(WebClient.builder(), properties);
        adapter = new IdentityAdapter(webClient, properties);
    }

    @Test
    void knownNpiReturnsIdentity() {
        server.when(request().withPath("/identite/1111111111"))
            .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json")
                .withBody("{\"nom\":\"AGOSSOU\",\"prenoms\":\"Jean\"}"));

        assertThat(adapter.findByNpi("1111111111")).contains(new Identity("AGOSSOU", "Jean"));
    }

    @Test
    void unknownNpiReturnsEmpty() {
        server.when(request().withPath("/identite/0000000000")).respond(response().withStatusCode(404));

        assertThat(adapter.findByNpi("0000000000")).isEmpty();
    }

    @Test
    void serverErrorMeansUnavailable() {
        server.when(request().withPath("/identite/5555555555")).respond(response().withStatusCode(500));

        assertThatThrownBy(() -> adapter.findByNpi("5555555555")).isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void slowServiceIsCutAtTheTotalTimeout() {
        server.when(request().withPath("/identite/2222222222"))
            .respond(response().withStatusCode(200).withDelay(TimeUnit.SECONDS, 10));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.findByNpi("2222222222")).isInstanceOf(IdentityUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void unreachableServiceMeansUnavailable() {
        IdentityClientProperties properties = new IdentityClientProperties();
        properties.setBaseUrl("http://localhost:1");
        properties.setIdentityPath("/identite/{npi}");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setTotalTimeout(Duration.ofMillis(500));
        IdentityAdapter unreachable = new IdentityAdapter(
            new IdentityClientConfiguration().identityWebClient(WebClient.builder(), properties), properties);

        assertThatThrownBy(() -> unreachable.findByNpi("1111111111")).isInstanceOf(IdentityUnavailableException.class);
    }
}
