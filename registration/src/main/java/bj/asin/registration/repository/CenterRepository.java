package bj.asin.registration.repository;

import bj.asin.registration.entity.Center;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CenterRepository extends JpaRepository<Center, Integer> {

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Center c where c.code = :code")
    Optional<Center> findByCodeForUpdate(@Param("code") String code);
}
