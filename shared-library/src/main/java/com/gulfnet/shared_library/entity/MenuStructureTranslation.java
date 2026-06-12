package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "menu_structure_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuStructureTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(name = "language_code")
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_structure_id", columnDefinition = "UUID")
    private MenuStructure menuStructure;

}
