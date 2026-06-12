package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PromotionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionTranslationRepository extends JpaRepository<PromotionTranslation, UUID> {
    
    @Query("SELECT pt FROM PromotionTranslation pt WHERE pt.promotion.id = :promotionId")
    List<PromotionTranslation> findAllByPromotionId(@Param("promotionId") UUID promotionId);
} 