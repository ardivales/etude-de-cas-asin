package bj.asin.registration.support;

import bj.asin.registration.integration.identity.Identity;
import bj.asin.registration.integration.identity.IdentityGateway;
import bj.asin.registration.integration.identity.IdentityUnavailableException;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class FakeIdentityGateway implements IdentityGateway {

    public enum Mode { KNOWN, UNKNOWN, UNAVAILABLE }

    private volatile Mode mode = Mode.KNOWN;
    private volatile long delayMillis;

    public void reset() {
        mode = Mode.KNOWN;
        delayMillis = 0;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public Optional<Identity> findByNpi(String npi) {
        pause();
        return switch (mode) {
            case KNOWN -> Optional.of(new Identity("DOSSOU", "Afi"));
            case UNKNOWN -> Optional.empty();
            case UNAVAILABLE -> throw new IdentityUnavailableException("indisponible", new TimeoutException());
        };
    }

    private void pause() {
        if (delayMillis == 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
