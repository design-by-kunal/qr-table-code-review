package com.gulfnet.usermanagement;

import com.gulfnet.shared_library.testing.messages.MessageKeyBundleTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Fails if message keys exist in the bundles but are not used anywhere in the
 * Java codebase (neither as MessageKeys constants nor in direct getMessage("...") calls),
 * after the literal-string fallback scan.
 *
 * <p>Complements {@link MessageKeyFullValidationTest}, which checks that all used keys exist in all bundles.</p>
 */
class MessageKeyUnusedDetectionTest {

    @Test
    void detectUnusedMessageKeys() {
        MessageKeyBundleTestSupport.assertNoUnusedBundleKeysViolation(MessageKeyUnusedDetectionTest.class);
    }
}
