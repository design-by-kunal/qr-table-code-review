package com.gulfnet.restaurantmanagement;

import com.gulfnet.shared_library.testing.messages.MessageKeyBundleTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Fails if message keys exist in the bundles but are not used anywhere in the
 * Java codebase (neither as MessageKeys constants nor in direct getMessage("...") calls),
 * after the literal-string fallback scan.
 *
 * <p>Complements {@link MessageKeyFullValidationTest}, which checks that all used keys exist in all bundles.</p>
 */
class MessageKeyUnusedDetectionTest {

    /**
     * Prefixes for keys that are intentionally resolved dynamically (e.g. by concatenating an enum name),
     * which this test cannot reliably detect as "used" via literal-string scanning.
     */
    private static final Set<String> IGNORED_UNUSED_KEY_PREFIXES = Set.of(
            "email.scheduled.report.type.",
            // Built from enum / entity names (see localizedActionLabel / localizedEntityTypeLabel); not full literals in source.
            "audit.trail.action.",
            "audit.trail.entity."
    );

    @Test
    void detectUnusedMessageKeys() {
        MessageKeyBundleTestSupport.assertNoUnusedBundleKeysViolation(
                MessageKeyUnusedDetectionTest.class,
                k -> IGNORED_UNUSED_KEY_PREFIXES.stream().noneMatch(k::startsWith));
    }
}
