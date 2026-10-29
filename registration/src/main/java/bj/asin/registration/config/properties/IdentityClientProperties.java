package bj.asin.registration.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "integration.identity")
public class IdentityClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String identityPath;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration totalTimeout;
}
