package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.KdsTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KdsTranslationRepository extends JpaRepository<KdsTranslation, UUID> {
    
    @Query("SELECT t FROM KdsTranslation t WHERE t.kds.id = :kdsId")
    List<KdsTranslation> findAllByKdsId(@Param("kdsId") UUID kdsId);

    @Query("SELECT t FROM KdsTranslation t WHERE t.kds.id = :kdsId")
    List<KdsTranslation> findAllByKdsIdWithLanguage(@Param("kdsId") UUID kdsId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM KdsTranslation t " +
            "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode AND t.kds.status <> 'DELETED'")
    boolean existsByNameIgnoreCaseAndLanguageCodeAndNotDeleted(@Param("name") String name, @Param("languageCode") String languageCode);

    boolean existsByName(String name);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM KdsTranslation t WHERE t.name = :name AND t.kds.id != :kdsId")
    boolean existsByNameAndKdsIdNot(@Param("name") String name, @Param("kdsId") UUID kdsId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM KdsTranslation t WHERE t.name = :name AND t.languageCode = :languageCode")
    boolean existsByNameAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

    @Query("SELECT t FROM KdsTranslation t WHERE t.kds.id = :kdsId AND t.languageCode = :languageCode")
    Optional<KdsTranslation> findByKdsIdAndLanguageCode(@Param("kdsId") UUID kdsId, @Param("languageCode") String languageCode);

    @Query("SELECT t FROM KdsTranslation t WHERE t.kds.id IN :kdsIds")
    List<KdsTranslation> findAllByKdsIdIn(@Param("kdsIds") List<UUID> kdsIds);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM KdsTranslation t " +
            "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
            "AND t.kds.restaurantId = :restaurantId AND t.kds.isDeleted = false")
    boolean existsByNameAndLanguageCodeAndRestaurantId(@Param("name") String name, 
                                                       @Param("languageCode") String languageCode, 
                                                       @Param("restaurantId") UUID restaurantId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM KdsTranslation t " +
            "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
            "AND t.kds.restaurantId = :restaurantId AND t.kds.id != :excludeKdsId AND t.kds.isDeleted = false")
    boolean existsByNameAndLanguageCodeAndRestaurantIdAndKdsIdNot(@Param("name") String name, 
                                                                   @Param("languageCode") String languageCode, 
                                                                   @Param("restaurantId") UUID restaurantId,
                                                                   @Param("excludeKdsId") UUID excludeKdsId);
}

