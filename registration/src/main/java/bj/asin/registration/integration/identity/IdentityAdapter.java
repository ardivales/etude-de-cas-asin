package bj.asin.registration.integration.identity;

import bj.asin.registration.config.properties.IdentityClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdentityAdapter implements IdentityGateway {

    @Qualifier("identityWebClient")
    private final WebClient identityWebClient;
    private final IdentityClientProperties properties;

    @Override
    public Optional<Identity> findByNpi(String npi) {
        try {
            return identityWebClient.get()
                .uri(properties.getIdentityPath(), npi)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return response.releaseBody().then(Mono.<Identity>empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.releaseBody().then(Mono.<Identity>error(
                            new IllegalStateException("Statut " + response.statusCode().value())));
                    }
                    return response.bodyToMono(Identity.class);
                })
                .timeout(properties.getTotalTimeout())
                .blockOptional();
        } catch (RuntimeException e) {
            throw new IdentityUnavailableException("Service d'identification indisponible", e);
        }
    }
}
