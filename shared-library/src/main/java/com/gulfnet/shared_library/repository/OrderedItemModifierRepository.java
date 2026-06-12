package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.OrderedItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderedItemModifierRepository extends JpaRepository<OrderedItemModifier, UUID> {
    List<OrderedItemModifier> findByOrderedItemId(UUID orderedItemId);

    /**
     * Batch fetch ordered item modifiers with all data needed for dashboard response building.
     * Includes ModifierGroup + translations and ModifierItem + translations to avoid lazy-loading N+1.
     */
    @Query("SELECT DISTINCT oim FROM OrderedItemModifier oim " +
            "JOIN FETCH oim.orderedItem oi " +
            "LEFT JOIN FETCH oim.modifierGroup mg " +
            "LEFT JOIN FETCH oim.modifierItem mi " +
            "LEFT JOIN FETCH mi.translations itr " +
            "WHERE oi.id IN :orderedItemIds")
    List<OrderedItemModifier> findByOrderedItemIdInWithRelations(
            @Param("orderedItemIds") List<UUID> orderedItemIds);

}
