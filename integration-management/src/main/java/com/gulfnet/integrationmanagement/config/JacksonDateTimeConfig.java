package com.gulfnet.integrationmanagement.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Enforces a single, consistent JSON datetime format across all API responses:
 * ISO-8601 datetime with trailing 'Z' and no fractional seconds, e.g. 2026-04-20T10:27:55Z.
 *
 * Important: 'Z' means UTC. For types that include an offset/zone we convert to a UTC instant
 * before formatting (and truncate fractional seconds).
 */
@Configuration
public class JacksonDateTimeConfig {

    private static final DateTimeFormatter ISO_UTC_Z_NO_FRACTION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter ISO_OFFSET_TIME_NO_FRACTION =
            DateTimeFormatter.ofPattern("HH:mm:ssXXX");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcInstantDateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("utc-instant-datetime");
            module.addSerializer(Instant.class, new InstantAsUtcSecondsSerializer());
            module.addSerializer(OffsetDateTime.class, new OffsetDateTimeAsUtcInstantSecondsSerializer());
            module.addSerializer(ZonedDateTime.class, new ZonedDateTimeAsUtcInstantSecondsSerializer());
            module.addSerializer(LocalDateTime.class, new LocalDateTimeAsUtcSecondsSerializer());
            module.addSerializer(OffsetTime.class, new OffsetTimeNoFractionSerializer());

            builder.modulesToInstall(new JavaTimeModule());
            builder.modulesToInstall(module);
        };
    }

    private static void writeInstantWithZ(JsonGenerator gen, Instant instant) throws IOException {
        gen.writeString(ISO_UTC_Z_NO_FRACTION.format(instant.truncatedTo(ChronoUnit.SECONDS)));
    }

    private static final class InstantAsUtcSecondsSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            writeInstantWithZ(gen, value);
        }
    }

    private static final class OffsetDateTimeAsUtcInstantSecondsSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            writeInstantWithZ(gen, value.toInstant());
        }
    }

    private static final class ZonedDateTimeAsUtcInstantSecondsSerializer extends JsonSerializer<ZonedDateTime> {
        @Override
        public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            writeInstantWithZ(gen, value.toInstant());
        }
    }

    /**
     * LocalDateTime has no timezone/offset; for API responses we treat it as UTC to keep output consistent.
     */
    private static final class LocalDateTimeAsUtcSecondsSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            writeInstantWithZ(gen, value.toInstant(ZoneOffset.UTC));
        }
    }

    private static final class OffsetTimeNoFractionSerializer extends JsonSerializer<OffsetTime> {
        @Override
        public void serialize(OffsetTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(ISO_OFFSET_TIME_NO_FRACTION.format(value.withNano(0)));
        }
    }
}

