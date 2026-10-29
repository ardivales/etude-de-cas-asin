package bj.asin.registration.service;

import bj.asin.registration.common.exception.BusinessException;
import bj.asin.registration.common.exception.InvalidRequestException;
import bj.asin.registration.common.exception.TemporaryFailureException;
import bj.asin.registration.dto.RegistrationDtos.RegistrationRequest;
import bj.asin.registration.entity.Exam;
import bj.asin.registration.entity.Registration;
import bj.asin.registration.integration.identity.Identity;
import bj.asin.registration.integration.identity.IdentityGateway;
import bj.asin.registration.integration.identity.IdentityUnavailableException;
import bj.asin.registration.repository.CenterRepository;
import bj.asin.registration.repository.ExamRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    static final String ALREADY_REGISTERED = "Vous êtes déjà inscrit à ce concours.";
    private static final String NPI_EXAM_CONSTRAINT = "ux_inscriptions_npi_concours";
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final ExamRepository examRepository;
    private final CenterRepository centerRepository;
    private final IdentityGateway identityGateway;
    private final RegistrationWriter registrationWriter;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public Registration register(RegistrationRequest request) {
        Exam exam = examRepository.findByCode(request.exam())
            .orElseThrow(() -> new InvalidRequestException("Concours inconnu."));
        if (!centerRepository.existsByCode(request.center())) {
            throw new InvalidRequestException("Centre d'examen inconnu.");
        }
        if (!RegistrationRules.isRegistrationOpen(clock.instant(), exam.getClosing())) {
            throw new BusinessException("CLOTURE", "Inscriptions closes pour ce concours.");
        }
        if (!RegistrationRules.isAgeAllowed(request.birthDate(), exam.getExamDate())) {
            throw new BusinessException("AGE", "Âge limite dépassé : 35 ans au 31 décembre de l'année du concours.");
        }

        Identity identity = null;
        String status = Registration.VALIDATED;
        try {
            identity = identityGateway.findByNpi(request.npi())
                .orElseThrow(() -> new BusinessException("NPI_INCONNU", "NPI inconnu du service d'identification."));
        } catch (IdentityUnavailableException e) {
            log.warn("Identité non vérifiée, inscription mise en attente : {}", e.getCause().toString());
            status = Registration.PENDING_VERIFICATION;
        }

        try {
            Registration registration = registrationWriter.save(request, exam, identity, status);
            meterRegistry.counter("inscriptions.enregistrees", "statut", registration.getStatus()).increment();
            return registration;
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                && NPI_EXAM_CONSTRAINT.equals(cve.getConstraintName())) {
                throw new BusinessException("DEJA_INSCRIT", ALREADY_REGISTERED);
            }
            throw new TemporaryFailureException("Inscription momentanément impossible, veuillez réessayer.", e);
        }
    }
}
