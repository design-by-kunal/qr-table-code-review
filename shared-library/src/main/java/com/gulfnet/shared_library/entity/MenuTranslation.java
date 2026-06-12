package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "menu_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "language_code", nullable = false)
    private String languageCode;

    @Column(nullable = false)
    private String name;

    private String description;
}
