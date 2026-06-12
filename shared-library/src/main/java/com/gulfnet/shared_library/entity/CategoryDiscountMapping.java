package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "category_discount_mapping")
public class CategoryDiscountMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_mapping_id", columnDefinition = "UUID")
    private MenuCategoryMapping menuCategoryMapping;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Discount getDiscount() { return discount; }
    public void setDiscount(Discount discount) { this.discount = discount; }
    public MenuCategoryMapping getMenuCategoryMapping() { return menuCategoryMapping; }
    public void setMenuCategoryMapping(MenuCategoryMapping menuCategoryMapping) { this.menuCategoryMapping = menuCategoryMapping; }
}
