package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.ShiftTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTranslationRepository extends JpaRepository<ShiftTranslation, UUID> {
    
    @Query("SELECT t FROM ShiftTranslation t WHERE t.shift.id = :shiftId")
    List<ShiftTranslation> findAllByShiftId(@Param("shiftId") UUID shiftId);

    @Query("SELECT t FROM ShiftTranslation t WHERE t.shift.id = :shiftId AND t.languageCode = :languageCode")
    Optional<ShiftTranslation> findByShiftIdAndLanguageCode(@Param("shiftId") UUID shiftId, @Param("languageCode") String languageCode);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ShiftTranslation t " +
           "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode")
    boolean existsByNameIgnoreCaseAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ShiftTranslation t " +
           "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode AND t.shift.id != :shiftId")
    boolean existsByNameAndLanguageCodeAndShiftIdNot(@Param("name") String name, @Param("languageCode") String languageCode, @Param("shiftId") UUID shiftId);

    // Find shift by translation name (searches in default language 'en' first, then any language)
    @Query("SELECT t.shift FROM ShiftTranslation t WHERE LOWER(t.name) = LOWER(:name) ORDER BY CASE WHEN t.languageCode = 'en' THEN 1 ELSE 2 END")
    java.util.List<Shift> findShiftsByName(@Param("name") String name);
    
    @Query("SELECT t.shift FROM ShiftTranslation t WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode")
    java.util.Optional<Shift> findShiftByNameAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

    // Batch loading methods for N+1 query fixes
    @Query("SELECT t FROM ShiftTranslation t WHERE t.shift.id IN :shiftIds")
    List<ShiftTranslation> findAllByShiftIdIn(@Param("shiftIds") List<UUID> shiftIds);
}
