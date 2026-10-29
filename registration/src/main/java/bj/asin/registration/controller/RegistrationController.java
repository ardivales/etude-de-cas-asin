package bj.asin.registration.controller;

import bj.asin.registration.dto.RegistrationDtos.RegistrationRequest;
import bj.asin.registration.dto.RegistrationDtos.RegistrationResponse;
import bj.asin.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inscriptions")
@RestController
@RequestMapping("/api/inscriptions")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "Inscrire un candidat à un concours",
        description = "Statut validee si l'identité est vérifiée, en_attente_verification si le service "
            + "d'identification ne répond pas en 5 secondes.")
    @ApiResponse(responseCode = "201", description = "Inscription enregistrée")
    @ApiResponse(responseCode = "400", description = "Données invalides",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Refus : déjà inscrit, même jour, centre complet, clôture, âge, NPI inconnu",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Échec temporaire, réessayer",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return RegistrationResponse.from(registrationService.register(request));
    }
}
