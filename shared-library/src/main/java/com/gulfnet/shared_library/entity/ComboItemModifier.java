package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "combo_item_modifier")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboItemModifier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_item_mapping_id", nullable = false)
    private ComboItemMapping comboItemMapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_item_id", nullable = false)
    private ModifierItem modifierItem;
}
