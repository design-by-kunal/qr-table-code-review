package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "category_kds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryKds {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_category_mapping_id", nullable = false)
    private MenuCategoryMapping menuCategoryMapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kds_id", nullable = false)
    private Kds kds;
}

