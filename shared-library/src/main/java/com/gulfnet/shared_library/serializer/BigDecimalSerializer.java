package com.gulfnet.shared_library.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Custom serializer for BigDecimal to prevent scientific notation in JSON
 * Formats BigDecimal as plain decimal number with 2 decimal places
 */
public class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // Format with 2 decimal places and use plain string representation (no scientific notation)
            BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
            // Use writeRawValue to write the plain string as a number (prevents scientific notation)
            gen.writeRawValue(scaled.toPlainString());
        }
    }
}

