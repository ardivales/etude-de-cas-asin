package bj.asin.registration.controller;

import bj.asin.registration.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest extends ApiTestSupport {

    @BeforeEach
    void slowIdentityService() {
        identityGateway.setDelayMillis(50);
    }

    @Test
    void neverExceedsCapacityNorDuplicatesTableNumbers() throws Exception {
        jdbc.update("UPDATE centres SET capacite = 10 WHERE code = 'L'");
        List<String> requests = IntStream.range(0, 40)
            .mapToObj(i -> body("20000000%02d".formatted(i), "DOUANES", "L"))
            .toList();

        List<HttpResponse<String>> responses = sendSimultaneously(requests);

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(10);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409)
            .hasSize(30)
            .allSatisfy(r -> assertThat(r.body()).contains("complet"));
        assertThat(jdbc.queryForList("SELECT numero_table FROM inscriptions ORDER BY numero_table", String.class))
            .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj("L-%04d"::formatted).toList());
    }

    @Test
    void registersOnlyOnceOnSimultaneousDoubleClicks() throws Exception {
        List<String> requests = IntStream.range(0, 15).mapToObj(i -> body("3000000000", "DOUANES", "C")).toList();

        List<HttpResponse<String>> responses = sendSimultaneously(requests);

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).hasSize(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscriptions", Integer.class)).isEqualTo(1);
    }

    @Test
    void registersOnlyOneExamPerDayUnderConcurrency() throws Exception {
        List<String> requests = IntStream.range(0, 10)
            .mapToObj(i -> i % 2 == 0 ? body("4000000000", "DOUANES", "C") : body("4000000000", "IMPOTS", "L"))
            .toList();

        List<HttpResponse<String>> responses = sendSimultaneously(requests);

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscriptions", Integer.class)).isEqualTo(1);
    }

    private List<HttpResponse<String>> sendSimultaneously(List<String> bodies) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(bodies.size())) {
            List<Future<HttpResponse<String>>> futures = bodies.stream()
                .map(json -> executor.submit(() -> {
                    start.await();
                    return post(json);
                }))
                .toList();
            start.countDown();
            List<HttpResponse<String>> responses = new java.util.ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }
}
