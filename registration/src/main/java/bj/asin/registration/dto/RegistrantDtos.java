package bj.asin.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class RegistrantDtos {

    private RegistrantDtos() {
    }

    public record RegistrantView(
        @JsonProperty("numeroTable") String tableNumber,
        @JsonProperty("npi") String npi,
        @JsonProperty("nom") String lastName,
        @JsonProperty("prenoms") String firstNames,
        @JsonProperty("statut") String status
    ) {
    }

    public record CandidateView(
        @JsonProperty("npi") String npi,
        @JsonProperty("nom") String lastName,
        @JsonProperty("prenoms") String firstNames
    ) {
    }
}
