package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "language")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "language_name", nullable = false)
    private String languageName;

    @Column(name = "code", nullable = false, unique = true)
    private String code;
}
