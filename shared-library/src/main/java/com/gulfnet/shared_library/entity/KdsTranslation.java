package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "kds_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "language_code", length = 255, nullable = false)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kds_id")
    private Kds kds;

}

