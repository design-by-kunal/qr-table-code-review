package com.gulfnet.shared_library.repository;  

import com.gulfnet.shared_library.entity.MenuTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param; 
import org.springframework.data.jpa.repository.Query;  

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuTranslationRepository extends JpaRepository<MenuTranslation, UUID> {

    // get all translations of a menu
    List<MenuTranslation> findByMenuId(UUID menuId);

    // find by menuId + languageCode
    MenuTranslation findByMenuIdAndLanguageCode(UUID menuId, String languageCode);

    // delete translations for a menu
    void deleteByMenuId(UUID menuId);

    boolean existsByNameAndLanguageCode(String name, String languageCode);
    
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuTranslation t " +
       "WHERE LOWER(t.name) = LOWER(:name) AND t.menu.isDeleted = false")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuTranslation t " +
    "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
    "AND t.menu.id != :menuId AND t.menu.isDeleted = false")
    boolean existsByNameAndLanguageCodeAndMenuIdNot(
     @Param("name") String name, 
     @Param("languageCode") String languageCode, 
     @Param("menuId") UUID menuId);

    /**
     * Same as {@link #existsByNameAndLanguageCodeAndMenuIdNot} but ignores translations on menus
     * that share {@code menuMasterId} (other versions of the same logical menu). When
     * {@code menuMasterId} is null, behaves like {@link #existsByNameAndLanguageCodeAndMenuIdNot}.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuTranslation t " +
    "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
    "AND t.menu.id != :menuId AND t.menu.isDeleted = false " +
    "AND (:menuMasterId IS NULL OR t.menu.menuMasterId IS NULL OR t.menu.menuMasterId <> :menuMasterId)")
    boolean existsByNameAndLanguageCodeAndMenuIdNotExcludingSameMaster(
            @Param("name") String name,
            @Param("languageCode") String languageCode,
            @Param("menuId") UUID menuId,
            @Param("menuMasterId") UUID menuMasterId);

}
