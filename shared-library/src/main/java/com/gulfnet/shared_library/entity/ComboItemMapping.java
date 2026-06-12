package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "combo_item_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboItemMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_group_id", nullable = false)
    private ComboGroup comboGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_item_mapping_id", nullable = false)
    private CategoryItemMapping categoryItemMapping;

    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;

}
