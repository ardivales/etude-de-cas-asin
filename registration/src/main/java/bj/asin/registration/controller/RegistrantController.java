package bj.asin.registration.controller;

import bj.asin.registration.dto.RegistrantDtos.CandidateView;
import bj.asin.registration.dto.RegistrantDtos.RegistrantView;
import bj.asin.registration.service.RegistrantQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Inscrits")
@RestController
@Validated
@RequiredArgsConstructor
public class RegistrantController {

    private final RegistrantQueryService registrantQueryService;

    @Operation(summary = "Liste des inscrits d'un centre pour un concours",
        description = "Une seule requête avec jointure, triée par numéro de table, paginée (100 lignes au plus).")
    @GetMapping("/api/centres/{code}/inscrits")
    public List<RegistrantView> listRegistrants(
        @PathVariable @Pattern(regexp = "[A-Z]", message = "Code de centre invalide.") String code,
        @RequestParam @Pattern(regexp = "[A-Z]{1,10}", message = "Code de concours invalide.") String concours,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "100") @Min(1) @Max(RegistrantQueryService.MAX_PAGE_SIZE) int size) {
        return registrantQueryService.listRegistrants(code, concours, page, size);
    }

    @Operation(summary = "Recherche de candidats par début de nom",
        description = "Requête paramétrée, 50 résultats au plus.")
    @GetMapping("/api/inscrits/recherche")
    public List<CandidateView> search(
        @RequestParam @Pattern(regexp = "[\\p{L}][\\p{L} '\\-]{0,59}", message = "Nom invalide.") String nom) {
        return registrantQueryService.searchByLastName(nom);
    }
}
