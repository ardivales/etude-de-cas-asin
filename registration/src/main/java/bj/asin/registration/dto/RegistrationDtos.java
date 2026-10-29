package bj.asin.registration.dto;

import bj.asin.registration.entity.Registration;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RegistrationRequest(
        @NotNull(message = "Le NPI est obligatoire.")
        @Pattern(regexp = "\\d{10}", message = "Le NPI doit comporter exactement 10 chiffres.")
        @JsonProperty("npi") String npi,

        @NotNull(message = "La date de naissance est obligatoire.")
        @Past(message = "La date de naissance doit être dans le passé.")
        @JsonProperty("naissance") LocalDate birthDate,

        @NotBlank(message = "Le concours est obligatoire.")
        @Size(max = 10, message = "Code de concours invalide.")
        @JsonProperty("concours") String exam,

        @NotBlank(message = "Le centre est obligatoire.")
        @Size(max = 1, message = "Code de centre invalide.")
        @JsonProperty("centre") String center
    ) {
    }

    public record RegistrationResponse(
        @JsonProperty("id") Integer id,
        @JsonProperty("numeroTable") String tableNumber,
        @JsonProperty("statut") String status
    ) {
        public static RegistrationResponse from(Registration registration) {
            return new RegistrationResponse(registration.getId(), registration.getTableNumber(), registration.getStatus());
        }
    }
}
