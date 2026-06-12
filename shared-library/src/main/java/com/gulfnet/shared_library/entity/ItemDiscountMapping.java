package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "item_discount_mapping")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemDiscountMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_item_mapping_id", columnDefinition = "UUID")
    private CategoryItemMapping categoryItemMapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;
}
