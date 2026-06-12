package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ItemModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemModifierGroupRepository extends JpaRepository<ItemModifierGroup, UUID> {

    List<ItemModifierGroup> findByItemIdAndIsDeletedFalse(UUID itemId);
    
    List<ItemModifierGroup> findByModifierGroupIdAndIsDeletedFalse(UUID modifierGroupId);
    
    Optional<ItemModifierGroup> findByItemIdAndModifierGroupIdAndIsDeletedFalse(UUID itemId, UUID modifierGroupId);
    
    boolean existsByItemIdAndModifierGroupIdAndIsDeletedFalse(UUID itemId, UUID modifierGroupId);
}

