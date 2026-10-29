package bj.asin.registration.repository;

import bj.asin.registration.entity.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RegistrationRepository extends JpaRepository<Registration, Integer> {

    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:key) AS TEXT)", nativeQuery = true)
    String lockCandidate(@Param("key") long key);

    @Query("select r.examId from Registration r, Exam e where e.id = r.examId and r.npi = :npi and e.examDate = :examDate")
    List<Integer> findExamIdsOnDate(@Param("npi") String npi, @Param("examDate") LocalDate examDate);

    long countByCenterId(Integer centerId);
}
