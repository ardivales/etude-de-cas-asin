package bj.asin.registration.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "DB_PASSWORD=unused")
@Import({TestcontainersConfiguration.class, ApiTestSupport.TestBeans.class})
public abstract class ApiTestSupport {

    public static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected FakeIdentityGateway identityGateway;

    @Autowired
    protected MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        FakeIdentityGateway fakeIdentityGateway() {
            return new FakeIdentityGateway();
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE inscriptions, candidats RESTART IDENTITY");
        jdbc.execute("""
            UPDATE centres SET dernier_numero = 0, capacite = CASE code
              WHEN 'C' THEN 6000 WHEN 'P' THEN 700 WHEN 'L' THEN 300 WHEN 'N' THEN 800 ELSE 600 END""");
        identityGateway.reset();
        clock.set(NOW);
    }

    protected HttpResponse<String> post(String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/inscriptions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected HttpResponse<String> get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected static String body(String npi, String exam, String center, String birthDate) {
        return """
            {"npi": "%s", "concours": "%s", "centre": "%s", "naissance": "%s"}""".formatted(npi, exam, center, birthDate);
    }

    protected static String body(String npi, String exam, String center) {
        return body(npi, exam, center, "1995-03-01");
    }
}
