package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ItemTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface ItemTranslationRepository extends JpaRepository<ItemTranslation, UUID> {
    @Query("SELECT t FROM ItemTranslation t WHERE t.item.id = :itemId")
    List<ItemTranslation> findAllByItemId(@Param("itemId") UUID itemId);

    @Query("SELECT t FROM ItemTranslation t WHERE t.item.id = :itemId")
    List<ItemTranslation> findAllByItemIdWithLanguage(@Param("itemId") UUID itemId);
    

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ItemTranslation t " +
    "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode AND t.item.status <> 'DELETED'")
boolean existsByNameIgnoreCaseAndLanguageCodeAndNotDeleted(@Param("name") String name, @Param("languageCode") String languageCode);

    boolean existsByName(String name);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ItemTranslation t WHERE t.name = :name AND t.item.id != :itemId")
    boolean existsByNameAndItemIdNot(@Param("name") String name, @Param("itemId") UUID itemId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ItemTranslation t WHERE t.name = :name AND t.languageCode = :languageCode")
    boolean existsByNameAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

    @Query("SELECT t FROM ItemTranslation t WHERE t.item.id = :itemId AND t.languageCode = :languageCode")
    Optional<ItemTranslation> findByItemIdAndLanguageCode(@Param("itemId") UUID itemId, @Param("languageCode") String languageCode);

    // Batch loading methods for N+1 query fixes
    @Query("SELECT t FROM ItemTranslation t WHERE t.item.id IN :itemIds")
    List<ItemTranslation> findAllByItemIdIn(@Param("itemIds") List<UUID> itemIds);
}