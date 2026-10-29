package bj.asin.registration.integration.identity;

import java.util.Optional;

public interface IdentityGateway {

    Optional<Identity> findByNpi(String npi);
}
