package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "discount_bxgy_item")  // Updated table name
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountBxgyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")  // Updated column name
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_item_ids")  // Updated column name and references CategoryItemMapping
    private CategoryItemMapping buyItemMapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "get_item_ids")  // Updated column name and references CategoryItemMapping
    private CategoryItemMapping getItemMapping;
} 
