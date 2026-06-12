package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboKds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboKdsRepository extends JpaRepository<ComboKds, UUID> {
    
    @Query("SELECT ck FROM ComboKds ck WHERE ck.kds.id = :kdsId")
    List<ComboKds> findByKdsId(@Param("kdsId") UUID kdsId);
    
    @Query("SELECT COUNT(ck) > 0 FROM ComboKds ck WHERE ck.combo.comboId = :comboId AND ck.kds.id = :kdsId")
    boolean existsByComboIdAndKdsId(@Param("comboId") UUID comboId, @Param("kdsId") UUID kdsId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM ComboKds ck WHERE ck.kds.id = :kdsId")
    void deleteByKdsId(@Param("kdsId") UUID kdsId);
}

