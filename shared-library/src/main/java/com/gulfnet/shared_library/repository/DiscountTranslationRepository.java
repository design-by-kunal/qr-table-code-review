package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.DiscountTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountTranslationRepository extends JpaRepository<DiscountTranslation, UUID> {

    List<DiscountTranslation> findByDiscountId(UUID discountId);

    DiscountTranslation findByDiscountIdAndLanguageCode(UUID discountId, String languageCode);

    void deleteByDiscountId(UUID discountId);

    boolean existsByNameAndLanguageCode(String name, String languageCode);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM DiscountTranslation t " +
           "WHERE LOWER(t.name) = LOWER(:name) AND t.discount.isDeleted = false")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM DiscountTranslation t " +
           "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
           "AND t.discount.id != :discountId AND t.discount.isDeleted = false")
    boolean existsByNameAndLanguageCodeAndDiscountIdNot(
            @Param("name") String name,
            @Param("languageCode") String languageCode,
            @Param("discountId") UUID discountId);
    
    /**
     * Batch load translations for multiple discounts
     */
    @Query("SELECT t FROM DiscountTranslation t WHERE t.discount.id IN :discountIds")
    List<DiscountTranslation> findByDiscountIds(@Param("discountIds") List<UUID> discountIds);
} 