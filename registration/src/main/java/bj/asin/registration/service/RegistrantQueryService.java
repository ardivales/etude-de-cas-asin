package bj.asin.registration.service;

import bj.asin.registration.common.exception.InvalidRequestException;
import bj.asin.registration.dto.RegistrantDtos.CandidateView;
import bj.asin.registration.dto.RegistrantDtos.RegistrantView;
import bj.asin.registration.repository.CandidateRepository;
import bj.asin.registration.repository.CenterRepository;
import bj.asin.registration.repository.ExamRepository;
import bj.asin.registration.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrantQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final RegistrationRepository registrationRepository;
    private final CandidateRepository candidateRepository;
    private final CenterRepository centerRepository;
    private final ExamRepository examRepository;

    public List<RegistrantView> listRegistrants(String center, String exam, int page, int size) {
        if (!centerRepository.existsByCode(center)) {
            throw new InvalidRequestException("Centre d'examen inconnu.");
        }
        if (examRepository.findByCode(exam).isEmpty()) {
            throw new InvalidRequestException("Concours inconnu.");
        }
        return registrationRepository.findRegistrants(center, exam, PageRequest.of(page, size));
    }

    public List<CandidateView> searchByLastName(String lastName) {
        return candidateRepository
            .findTop50ByLastNameStartingWithOrderByLastNameAscFirstNamesAsc(lastName.trim().toUpperCase(Locale.ROOT))
            .stream()
            .map(c -> new CandidateView(c.getNpi(), c.getLastName(), c.getFirstNames()))
            .toList();
    }
}
