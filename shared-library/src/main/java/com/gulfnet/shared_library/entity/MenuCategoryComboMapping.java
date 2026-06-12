package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "menu_category_combo_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuCategoryComboMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_mapping_id", nullable = false)
    private MenuCategoryMapping menuCategoryMapping;

}

