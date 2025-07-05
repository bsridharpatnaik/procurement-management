package com.sungroup.procurement.specification;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class for common specification operations
 */
public abstract class BaseSpecification<T> {

    /**
     * Creates a specification for soft delete filter
     */
    public static <T> Specification<T> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    /**
     * Creates a specification for equality check
     */
    public static <T> Specification<T> fieldEquals(String fieldName, Object value) {
        return (root, query, cb) -> {
            if (value == null) return cb.conjunction();
            return cb.equal(root.get(fieldName), value);
        };
    }

    /**
     * Creates a specification for IN clause
     */
    public static <T> Specification<T> fieldIn(String fieldName, Collection<?> values) {
        return (root, query, cb) -> {
            if (values == null || values.isEmpty()) return cb.conjunction();
            return root.get(fieldName).in(values);
        };
    }

    /**
     * Creates a specification for LIKE clause (case-insensitive)
     */
    public static <T> Specification<T> fieldContains(String fieldName, String value) {
        return (root, query, cb) -> {
            if (value == null || value.trim().isEmpty()) return cb.conjunction();
            return cb.like(cb.lower(root.get(fieldName)), "%" + value.toLowerCase() + "%");
        };
    }

    /**
     * Creates a specification for date range
     */
    public static <T> Specification<T> dateBetween(String fieldName, LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) return cb.conjunction();

            if (startDate != null && endDate != null) {
                return cb.between(root.get(fieldName), startDate, endDate);
            } else if (startDate != null) {
                return cb.greaterThanOrEqualTo(root.get(fieldName), startDate);
            } else {
                return cb.lessThanOrEqualTo(root.get(fieldName), endDate);
            }
        };
    }

    /**
     * Creates a specification for boolean field
     */
    public static <T> Specification<T> booleanEquals(String fieldName, Boolean value) {
        return (root, query, cb) -> {
            if (value == null) return cb.conjunction();
            return cb.equal(root.get(fieldName), value);
        };
    }

    /**
     * Creates a specification for nested field equality
     */
    public static <T> Specification<T> nestedFieldEquals(String parentField, String childField, Object value) {
        return (root, query, cb) -> {
            if (value == null) return cb.conjunction();
            return cb.equal(root.get(parentField).get(childField), value);
        };
    }

    /**
     * Creates a specification for nested field IN clause
     */
    public static <T> Specification<T> nestedFieldIn(String parentField, String childField, Collection<?> values) {
        return (root, query, cb) -> {
            if (values == null || values.isEmpty()) return cb.conjunction();
            return root.get(parentField).get(childField).in(values);
        };
    }

    /**
     * Combines multiple specifications with AND
     */
    @SafeVarargs
    public static <T> Specification<T> combineWithAnd(Specification<T>... specifications) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Specification<T> spec : specifications) {
                if (spec != null) {
                    Predicate predicate = spec.toPredicate(root, query, cb);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Combines multiple specifications with OR
     */
    @SafeVarargs
    public static <T> Specification<T> combineWithOr(Specification<T>... specifications) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Specification<T> spec : specifications) {
                if (spec != null) {
                    Predicate predicate = spec.toPredicate(root, query, cb);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}