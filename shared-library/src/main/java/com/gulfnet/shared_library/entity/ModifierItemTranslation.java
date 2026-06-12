package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "modifier_item_translation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModifierItemTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_item_id", nullable = false)
    private ModifierItem modifierItem;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

}