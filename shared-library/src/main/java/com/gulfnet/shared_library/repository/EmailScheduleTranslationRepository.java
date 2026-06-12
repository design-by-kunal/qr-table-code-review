package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.EmailScheduleTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmailScheduleTranslationRepository extends JpaRepository<EmailScheduleTranslation, UUID> {

    @Query("SELECT t FROM EmailScheduleTranslation t WHERE t.emailSchedule.id = :scheduleId")
    List<EmailScheduleTranslation> findAllByScheduleId(@Param("scheduleId") UUID scheduleId);
}

