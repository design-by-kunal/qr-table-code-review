package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.gulfnet.shared_library.enums.EntityStatus;
import java.util.UUID;

@Entity
@Table(name = "menu_category_mapping")
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", columnDefinition = "UUID")
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", columnDefinition = "UUID")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id", columnDefinition = "UUID")
    private Category parentCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EntityStatus status;

}
