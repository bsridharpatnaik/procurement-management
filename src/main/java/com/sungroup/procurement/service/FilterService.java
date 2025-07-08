package com.sungroup.procurement.service;

import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.request.FilterAttributeData;
import com.sungroup.procurement.entity.enums.UserRole;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilterService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Extract string values from filter data
     */
    public List<String> getStringValues(FilterDataList filterData, String fieldName) {
        if (filterData == null || filterData.getFilterData() == null) {
            return null;
        }

        return filterData.getFilterData().stream()
                .filter(filter -> fieldName.equalsIgnoreCase(filter.getAttrName()))
                .findFirst()
                .map(FilterAttributeData::getAttrValue)
                .orElse(null);
    }

    /**
     * Extract single string value from filter data
     */
    public String getStringValue(FilterDataList filterData, String fieldName) {
        List<String> values = getStringValues(filterData, fieldName);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Extract long values from filter data
     */
    public List<Long> getLongValues(FilterDataList filterData, String fieldName) {
        List<String> stringValues = getStringValues(filterData, fieldName);
        if (stringValues == null) return null;

        return stringValues.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    /**
     * Extract single long value from filter data
     */
    public Long getLongValue(FilterDataList filterData, String fieldName) {
        List<Long> values = getLongValues(filterData, fieldName);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Extract boolean value from filter data
     */
    public Boolean getBooleanValue(FilterDataList filterData, String fieldName) {
        String value = getStringValue(filterData, fieldName);
        if (value == null) return null;
        return Boolean.parseBoolean(value);
    }

    /**
     * Extract UserRole values from filter data
     */
    public List<UserRole> getUserRoleValues(FilterDataList filterData, String fieldName) {
        List<String> stringValues = getStringValues(filterData, fieldName);
        if (stringValues == null) return null;

        return stringValues.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> {
                    try {
                        return UserRole.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    /**
     * Extract date value from filter data
     */
    public LocalDateTime getDateValue(FilterDataList filterData, String fieldName) {
        String value = getStringValue(filterData, fieldName);
        if (value == null || value.trim().isEmpty()) return null;

        try {
            return LocalDateTime.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Extract date range from filter data
     */
    public DateRange getDateRange(FilterDataList filterData, String startFieldName, String endFieldName) {
        LocalDateTime startDate = getDateValue(filterData, startFieldName);
        LocalDateTime endDate = getDateValue(filterData, endFieldName);

        if (startDate == null && endDate == null) return null;

        return new DateRange(startDate, endDate);
    }

    /**
     * Combine multiple specifications with AND
     */
    @SafeVarargs
    public final <T> Specification<T> combineWithAnd(Specification<T>... specifications) {
        Specification<T> result = Specification.where(null);

        for (Specification<T> spec : specifications) {
            if (spec != null) {
                result = result.and(spec);
            }
        }

        return result;
    }

    /**
     * Combine multiple specifications with OR
     */
    @SafeVarargs
    public final <T> Specification<T> combineWithOr(Specification<T>... specifications) {
        Specification<T> result = null;

        for (Specification<T> spec : specifications) {
            if (spec != null) {
                if (result == null) {
                    result = spec;
                } else {
                    result = result.or(spec);
                }
            }
        }

        return result;
    }

    /**
     * Extract integer value from filter data
     */
    public Integer getIntegerValue(FilterDataList filterData, String fieldName) {
        String value = getStringValue(filterData, fieldName);
        if (value == null || value.trim().isEmpty()) return null;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract enum values from filter data
     */
    public <E extends Enum<E>> List<E> getEnumValues(FilterDataList filterData, String fieldName, Class<E> enumClass) {
        List<String> stringValues = getStringValues(filterData, fieldName);
        if (stringValues == null) return null;

        return stringValues.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> {
                    try {
                        return Enum.valueOf(enumClass, value.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    /**
     * Helper class for date ranges
     */
    public static class DateRange {
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;

        public DateRange(LocalDateTime startDate, LocalDateTime endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }
    }
}