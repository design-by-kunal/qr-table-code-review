package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import com.gulfnet.shared_library.enums.ItemOrderType;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Builder
@Table(name = "category_item_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryItemMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_mapping_id", columnDefinition = "UUID")
    private MenuCategoryMapping menuCategoryMapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", columnDefinition = "UUID")
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_order_type")
    private ItemOrderType itemOrderType;
}