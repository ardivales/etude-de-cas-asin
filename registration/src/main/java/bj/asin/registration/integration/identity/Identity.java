package bj.asin.registration.integration.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Identity(@JsonProperty("nom") String lastName, @JsonProperty("prenoms") String firstNames) {
}
