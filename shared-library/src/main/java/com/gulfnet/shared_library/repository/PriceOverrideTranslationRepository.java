package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PriceOverrideTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceOverrideTranslationRepository extends JpaRepository<PriceOverrideTranslation, UUID> {

    List<PriceOverrideTranslation> findByPriceOverrideId(UUID priceOverrideId);

    PriceOverrideTranslation findByPriceOverrideIdAndLanguageCode(UUID priceOverrideId, String languageCode);

    void deleteByPriceOverrideId(UUID priceOverrideId);
    
    @Query("SELECT t FROM PriceOverrideTranslation t WHERE t.priceOverride.id IN :priceOverrideIds")
    List<PriceOverrideTranslation> findAllByPriceOverrideIdIn(@Param("priceOverrideIds") List<UUID> priceOverrideIds);
}

