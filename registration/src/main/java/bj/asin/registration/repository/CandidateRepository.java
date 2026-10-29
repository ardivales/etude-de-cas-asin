package bj.asin.registration.repository;

import bj.asin.registration.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Integer> {

    boolean existsByNpi(String npi);
}
