package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LanguageRepository extends JpaRepository<Language, UUID> {
    Optional<Language> findByCode(String code);
} 