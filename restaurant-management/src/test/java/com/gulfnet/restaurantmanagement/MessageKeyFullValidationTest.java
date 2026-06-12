package com.gulfnet.restaurantmanagement;

import com.gulfnet.shared_library.testing.messages.MessageKeyBundleTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Fails if any message key referenced from {@code MessageKeys} constants or
 * {@code getMessage("...")} calls is missing from the English, Japanese, or Thai bundles.
 */
class MessageKeyFullValidationTest {

    @Test
    void validateAllMessageKeysExistInAllLanguages() {
        MessageKeyBundleTestSupport.assertUsedKeysExistInAllBundles(
                MessageKeyFullValidationTest.class,
                "❌ Missing translations:\n\n");
    }
}
