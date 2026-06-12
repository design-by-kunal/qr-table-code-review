package com.gulfnet.shared_library.mapper;

import com.gulfnet.shared_library.entity.RestaurantOperatingHours;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.model.request.RestaurantOperatingHoursRequest;
import com.gulfnet.shared_library.model.response.dto.OperatingHourDto;
import org.mapstruct.Mapper;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.model.response.dto.OperatingHourDto.Slot;
import com.gulfnet.shared_library.entity.OperatingHourSlot;

@Mapper(componentModel = "spring")
public interface RestaurantOperatingHoursMapper {
    /**
     * Converts a list of restaurant operating hours entities to a map grouped by day of week.
     * Aggregates multiple entries for the same day, determines if the day is closed,
     * and collects all time slots for open days.
     *
     * @param entities list of restaurant operating hours entities
     * @return map of day of week to operating hour DTO, with slots aggregated for each day
     */
    default Map<DayOfWeek, OperatingHourDto> toOperatingHoursMap(List<RestaurantOperatingHours> entities) {
        return entities.stream()
            .collect(Collectors.groupingBy(RestaurantOperatingHours::getDayOfWeek))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    OperatingHourDto dto = new OperatingHourDto();
                    dto.setDayOfWeek(entry.getKey());
                    dto.setIsClosed(entry.getValue().stream().allMatch(RestaurantOperatingHours::getIsClosed));
                    List<Slot> slots = entry.getValue().stream()
                        .filter(e -> !e.getIsClosed() && e.getSlots() != null)
                        .flatMap(e -> e.getSlots().stream())
                        .map(s -> new Slot(
                            s.getFromTime(),
                            s.getToTime()))
                        .collect(Collectors.toList());
                    dto.setSlots(slots);
                    return dto;
                }
            ));
    }

    /**
     * Converts a single restaurant operating hours entity to an operating hour DTO.
     * Maps the day of week, closed status, and time slots (if the restaurant is open).
     *
     * @param entity the restaurant operating hours entity to convert
     * @return operating hour DTO with day, closed status, and time slots
     */
    default OperatingHourDto toOperatingHoursDto(RestaurantOperatingHours entity) {
        OperatingHourDto dto = new OperatingHourDto();
        dto.setDayOfWeek(entity.getDayOfWeek());
        dto.setIsClosed(entity.getIsClosed());
        if (!entity.getIsClosed() && entity.getSlots() != null) {
            dto.setSlots(entity.getSlots().stream()
                .map(s -> new Slot(
                    s.getFromTime(),
                    s.getToTime()))
                .collect(Collectors.toList()));
        } else {
            dto.setSlots(List.of());
        }
        return dto;
    }

    default List<RestaurantOperatingHours> toEntities(RestaurantOperatingHoursRequest request) {
        return toEntities(request, null);
    }

    /**
     * Converts a restaurant operating hours request to a list of restaurant operating hours entities.
     * Handles both map-based requests (multiple days) and single day requests.
     * Sets the restaurant reference and created by user, and maps time slots.
     *
     * @param request      the restaurant operating hours request containing day(s) and time slots
     * @param createdByUser the user who created these operating hours (can be null)
     * @return list of restaurant operating hours entities ready for persistence
     */
    default List<RestaurantOperatingHours> toEntities(RestaurantOperatingHoursRequest request, com.gulfnet.shared_library.entity.User createdByUser) {
        List<RestaurantOperatingHours> entities = new ArrayList<>();
        if (request.getOperatingHours() != null && !request.getOperatingHours().isEmpty()) {
            for (var dto : request.getOperatingHours()) {
                RestaurantOperatingHours entity = new RestaurantOperatingHours();
                entity.setDayOfWeek(dto.getDayOfWeek());
                entity.setIsClosed(Boolean.TRUE.equals(dto.getIsClosed()));
                entity.setCreatedByUser(createdByUser);
                // Set the restaurant reference
                com.gulfnet.shared_library.entity.Restaurant restaurant = new com.gulfnet.shared_library.entity.Restaurant();
                restaurant.setId(request.getRestaurantId());
                entity.setRestaurant(restaurant);
                if (dto.getSlots() != null) {
                    List<OperatingHourSlot> slots = dto.getSlots().stream()
                        .map((com.gulfnet.shared_library.model.request.RestaurantOperatingHoursRequest.Slot s) -> OperatingHourSlot.builder()
                            .fromTime(s.getFromTime())
                            .toTime(s.getToTime())
                            .build())
                        .collect(Collectors.toList());
                    slots.forEach(slot -> slot.setRestaurantOperatingHours(entity));
                    entity.setSlots(slots);
                }
                entities.add(entity);
            }
        } else if (request.getDayOfWeek() != null) {
            RestaurantOperatingHours entity = new RestaurantOperatingHours();
            entity.setDayOfWeek(request.getDayOfWeek());
            entity.setIsClosed(Boolean.TRUE.equals(request.getIsClosed()));
            entity.setCreatedByUser(createdByUser);
            // Set the restaurant reference
            com.gulfnet.shared_library.entity.Restaurant restaurant = new com.gulfnet.shared_library.entity.Restaurant();
            restaurant.setId(request.getRestaurantId());
            entity.setRestaurant(restaurant);
            if (request.getSlots() != null) {
                List<OperatingHourSlot> slots = request.getSlots().stream()
                    .map((com.gulfnet.shared_library.model.request.RestaurantOperatingHoursRequest.Slot s) -> OperatingHourSlot.builder()
                        .fromTime(s.getFromTime())
                        .toTime(s.getToTime())
                        .build())
                    .collect(Collectors.toList());
                slots.forEach(slot -> slot.setRestaurantOperatingHours(entity));
                entity.setSlots(slots);
            }
            entities.add(entity);
        }
        return entities;
    }
}
