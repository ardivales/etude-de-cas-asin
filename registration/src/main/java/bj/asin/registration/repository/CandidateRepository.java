package bj.asin.registration.repository;

import bj.asin.registration.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Integer> {

    boolean existsByNpi(String npi);

    List<Candidate> findTop50ByLastNameStartingWithOrderByLastNameAscFirstNamesAsc(String prefix);
}
