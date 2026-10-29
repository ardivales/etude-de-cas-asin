package bj.asin.registration.service;

import bj.asin.registration.common.exception.BusinessException;
import bj.asin.registration.common.exception.InvalidRequestException;
import bj.asin.registration.dto.RegistrationDtos.RegistrationRequest;
import bj.asin.registration.entity.Candidate;
import bj.asin.registration.entity.Center;
import bj.asin.registration.entity.Exam;
import bj.asin.registration.entity.Registration;
import bj.asin.registration.integration.identity.Identity;
import bj.asin.registration.repository.CandidateRepository;
import bj.asin.registration.repository.CenterRepository;
import bj.asin.registration.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationWriter {

    private final RegistrationRepository registrationRepository;
    private final CenterRepository centerRepository;
    private final CandidateRepository candidateRepository;
    private final Clock clock;

    @Transactional
    public Registration save(RegistrationRequest request, Exam exam, Identity identity, String status) {
        registrationRepository.lockCandidate(Long.parseLong(request.npi()));

        List<Integer> examIds = registrationRepository.findExamIdsOnDate(request.npi(), exam.getExamDate());
        if (examIds.contains(exam.getId())) {
            throw new BusinessException(RegistrationService.ALREADY_REGISTERED);
        }
        if (!examIds.isEmpty()) {
            throw new BusinessException("Vous êtes déjà inscrit à un concours dont l'épreuve a lieu le même jour.");
        }

        Center center = centerRepository.findByCodeForUpdate(request.center())
            .orElseThrow(() -> new InvalidRequestException("Centre d'examen inconnu."));
        if (registrationRepository.countByCenterId(center.getId()) >= center.getCapacity()) {
            throw new BusinessException("Ce centre d'examen est complet.");
        }
        String tableNumber = center.assignNextTableNumber();

        Instant now = clock.instant();
        if (identity != null && !candidateRepository.existsByNpi(request.npi())) {
            candidateRepository.save(new Candidate(
                request.npi(), identity.lastName(), identity.firstNames(), request.birthDate(), now));
        }
        return registrationRepository.save(
            new Registration(exam.getId(), center.getId(), request.npi(), tableNumber, status, now));
    }
}
