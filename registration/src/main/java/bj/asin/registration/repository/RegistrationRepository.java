package bj.asin.registration.repository;

import bj.asin.registration.dto.RegistrantDtos.RegistrantView;
import bj.asin.registration.entity.Registration;
import org.springframework.data.domain.Pageable;
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

    @Query("""
        select new bj.asin.registration.dto.RegistrantDtos$RegistrantView(r.tableNumber, r.npi, c.lastName, c.firstNames, r.status)
        from Registration r
        join Center ce on ce.id = r.centerId
        join Exam e on e.id = r.examId
        left join Candidate c on c.npi = r.npi
        where ce.code = :center and e.code = :exam
        order by r.tableNumber""")
    List<RegistrantView> findRegistrants(@Param("center") String center, @Param("exam") String exam, Pageable pageable);
}
