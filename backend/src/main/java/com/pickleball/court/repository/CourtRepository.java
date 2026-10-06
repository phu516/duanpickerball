package com.pickleball.court.repository;

import com.pickleball.court.entity.Court;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtRepository extends JpaRepository<Court, Long> {
    List<Court> findByStatusOrderByCodeAsc(String status);
    List<Court> findByStatusAndLockedFalseOrderByCodeAsc(String status);
    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :id")
    Court lockById(@Param("id") Long id);
}
