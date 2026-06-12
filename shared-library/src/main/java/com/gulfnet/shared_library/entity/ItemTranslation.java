package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "item_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemTranslation {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "language_code", length = 255, nullable = false)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "description", length = 255)
    private String description;

}