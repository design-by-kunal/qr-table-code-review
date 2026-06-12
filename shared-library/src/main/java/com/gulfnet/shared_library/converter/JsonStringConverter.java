package com.gulfnet.shared_library.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter
@Slf4j
public class JsonStringConverter implements AttributeConverter<String, PGobject> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a JSON string entity attribute to a PostgreSQL JSONB column value.
     * Validates and canonicalizes the JSON before storing it in the database.
     *
     * @param attribute the JSON string to convert (can be null or empty)
     * @return PGobject with type "jsonb" containing the validated JSON, or null if input is null/empty
     * @throws IllegalArgumentException if the input string is not valid JSON
     */
    @Override
    public PGobject convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.trim().isEmpty()) {
            return null;
        }
        try {
            // Validate and canonicalize JSON
            Object json = objectMapper.readTree(attribute);
            String jsonString = objectMapper.writeValueAsString(json);

            // Create PGobject for PostgreSQL JSONB
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(jsonString);
            return pgObject;
        } catch (JsonProcessingException | SQLException e) {
            log.error("Invalid JSON string for database column: {}", attribute, e);
            throw new IllegalArgumentException("Invalid JSON string: " + attribute, e);
        }
    }

    /**
     * Converts a PostgreSQL JSONB column value to a JSON string entity attribute.
     * Validates that the database value contains valid JSON before returning it.
     *
     * @param dbData the PGobject from the database (can be null)
     * @return the JSON string value, or null if dbData is null or has no value
     * @throws IllegalArgumentException if the database value is not valid JSON
     */
    @Override
    public String convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) {
            return null;
        }
        String value = dbData.getValue();
        if (value == null) {
            return null;
        }
        try {
            // Validate JSON
            objectMapper.readTree(value);
            return value;
        } catch (JsonProcessingException e) {
            log.error("Invalid JSON string from database: {}", value, e);
            throw new IllegalArgumentException("Invalid JSON string from database: " + value, e);
        }
    }
}
