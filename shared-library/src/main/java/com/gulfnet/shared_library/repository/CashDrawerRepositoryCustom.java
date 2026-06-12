package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashDrawer;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface CashDrawerRepositoryCustom {

    Page<CashDrawer> findByRestaurantIdWithFilters(
            UUID restaurantId,
            EntityStatus status,
            String search,
            Pageable pageable,
            String sortField,
            Sort.Direction direction);

    List<CashDrawer> findActiveDrawersByRestaurantIdOrderByEnglishName(UUID restaurantId);
}
