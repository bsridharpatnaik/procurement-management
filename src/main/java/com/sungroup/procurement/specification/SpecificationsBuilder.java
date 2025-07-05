package com.sungroup.procurement.specification;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterAttributeData;
import com.sungroup.procurement.dto.request.FilterDataList;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SpecificationsBuilder<T> {

    private final String dateFormat = ProjectConstants.dateFormat;

    // #######################################//
    // Level 0 - Direct Field Operations //
    // #######################################//

    public Specification<T> whereDirectFieldContains(String key, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                        cb.like(cb.lower(root.get(key)), "%" + name.toLowerCase() + "%");
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereDirectBooleanFieldEquals(String key, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                        cb.equal(root.get(key), Boolean.parseBoolean(name));
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereDirectFieldEquals(String key, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                        cb.equal(root.get(key), name);
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereDirectLongFieldEquals(String key, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    Long value = Long.parseLong(name);
                    Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                            cb.equal(root.get(key), value);
                    finalSpec = specOrCondition(finalSpec, internalSpec);
                } catch (NumberFormatException e) {
                    // Skip invalid number
                }
            }
        }
        return finalSpec;
    }

    public Specification<T> whereDirectFieldDateGreaterThan(String key, List<String> startDates) {
        if (startDates == null || startDates.isEmpty()) return null;

        try {
            LocalDateTime startDate = LocalDateTime.parse(startDates.get(0), DateTimeFormatter.ofPattern(dateFormat));
            return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                    cb.greaterThanOrEqualTo(root.get(key), startDate);
        } catch (Exception e) {
            return null;
        }
    }

    public Specification<T> whereDirectFieldDateLessThan(String key, List<String> endDates) {
        if (endDates == null || endDates.isEmpty()) return null;

        try {
            LocalDateTime endDate = LocalDateTime.parse(endDates.get(0), DateTimeFormatter.ofPattern(dateFormat));
            return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                    cb.lessThanOrEqualTo(root.get(key), endDate);
        } catch (Exception e) {
            return null;
        }
    }

    public Specification<T> whereDirectFieldDateBetween(String key, String startDate, String endDate) {
        if (startDate == null || endDate == null) return null;

        try {
            LocalDateTime start = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern(dateFormat));
            LocalDateTime end = LocalDateTime.parse(endDate, DateTimeFormatter.ofPattern(dateFormat));

            return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                    cb.between(root.get(key), start, end);
        } catch (Exception e) {
            return null;
        }
    }

    public Specification<T> whereDirectFieldBigDecimalBetween(String key, BigDecimal lowerLimit, BigDecimal upperLimit) {
        if (lowerLimit == null || upperLimit == null) return null;

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.between(root.get(key), lowerLimit, upperLimit);
    }

    public Specification<T> whereDirectFieldBigDecimalGreaterThan(String key, BigDecimal value) {
        if (value == null) return null;

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.greaterThan(root.get(key), value);
    }

    // #######################################//
    // Level 1 - Child Field Operations //
    // #######################################//

    public Specification<T> whereChildFieldContains(String childTable, String childFieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                        cb.like(cb.lower(root.get(childTable).get(childFieldName)), "%" + name.toLowerCase() + "%");
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereChildFieldEquals(String childTable, String childFieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                        cb.equal(root.get(childTable).get(childFieldName), name);
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereChildLongFieldEquals(String childTable, String childFieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    Long value = Long.parseLong(name);
                    Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                            cb.equal(root.get(childTable).get(childFieldName), value);
                    finalSpec = specOrCondition(finalSpec, internalSpec);
                } catch (NumberFormatException e) {
                    // Skip invalid number
                }
            }
        }
        return finalSpec;
    }

    // #######################################//
    // Level 2 - Join Operations //
    // #######################################//

    public Specification<T> whereJoinFieldContains(String joinTable, String fieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
                    Join<Object, Object> join = root.join(joinTable, JoinType.INNER);
                    return cb.like(cb.lower(join.get(fieldName)), "%" + name.toLowerCase() + "%");
                };
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    public Specification<T> whereJoinFieldEquals(String joinTable, String fieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        Specification<T> finalSpec = null;
        for (String name : names) {
            if (name != null) {
                Specification<T> internalSpec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
                    Join<Object, Object> join = root.join(joinTable, JoinType.INNER);
                    return cb.equal(join.get(fieldName), name);
                };
                finalSpec = specOrCondition(finalSpec, internalSpec);
            }
        }
        return finalSpec;
    }

    // #######################################//
    // Collection Operations //
    // #######################################//

    public Specification<T> whereCollectionContains(String collectionField, String fieldName, List<String> names) {
        if (names == null || names.isEmpty()) return null;

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Join<Object, Object> join = root.join(collectionField, JoinType.INNER);
            Expression<String> fieldExpression = join.get(fieldName);
            query.distinct(true);
            return fieldExpression.in(names);
        };
    }

    // #######################################//
    // Utility Methods //
    // #######################################//

    public Specification<T> specAndCondition(Specification<T> finalSpec, Specification<T> internalSpec) {
        if (finalSpec == null)
            return internalSpec;
        else
            return finalSpec.and(internalSpec);
    }

    public Specification<T> specOrCondition(Specification<T> finalSpec, Specification<T> internalSpec) {
        if (finalSpec == null)
            return internalSpec;
        else
            return finalSpec.or(internalSpec);
    }

    // #######################################//
    // Filter Data Extraction Utilities //
    // #######################################//

    public static List<String> fetchValueFromFilterList(FilterDataList filterDataList, String field) {
        if (filterDataList == null || filterDataList.getFilterData() == null) {
            return null;
        }

        return filterDataList.getFilterData().stream()
                .filter(filterData -> filterData.getAttrName().equalsIgnoreCase(field))
                .findFirst()
                .map(FilterAttributeData::getAttrValue)
                .orElse(null);
    }

    // #######################################//
    // Soft Delete Filter //
    // #######################################//

    public Specification<T> excludeDeleted() {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("isDeleted"), false);
    }

    // #######################################//
    // Factory Access Filter //
    // #######################################//

    public Specification<T> whereFactoryIdIn(List<Long> factoryIds) {
        if (factoryIds == null || factoryIds.isEmpty()) return null;

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                root.get("factory").get("id").in(factoryIds);
    }
}