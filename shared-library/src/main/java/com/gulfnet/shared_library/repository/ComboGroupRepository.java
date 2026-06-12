package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboGroupRepository extends JpaRepository<ComboGroup, UUID> {
    List<ComboGroup> findByComboComboId(UUID comboId);
}