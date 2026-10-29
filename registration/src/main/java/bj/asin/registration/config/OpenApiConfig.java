package bj.asin.registration.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
    title = "API Inscriptions aux concours",
    version = "1.0",
    description = "Enregistrement d'une inscription corrigé : pas de numéro en double, pas de dépassement de capacité, "
        + "pas de double inscription, service d'identification limité à 5 secondes."))
public class OpenApiConfig {
}
