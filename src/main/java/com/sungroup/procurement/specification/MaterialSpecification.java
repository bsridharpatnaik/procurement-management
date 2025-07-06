package com.sungroup.procurement.specification;

import com.sungroup.procurement.entity.Material;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MaterialSpecification extends BaseSpecification<Material> {

    public static Specification<Material> isNotDeleted() {
        return BaseSpecification.isNotDeleted();
    }

    public static Specification<Material> hasName(String name) {
        return fieldContains("name", name);
    }

    public static Specification<Material> hasUnit(String unit) {
        return fieldContains("unit", unit);
    }

    public static Specification<Material> hasUnits(List<String> units) {
        return fieldIn("unit", units);
    }

    public static Specification<Material> isImportFromChina(Boolean importFromChina) {
        return booleanEquals("importFromChina", importFromChina);
    }

    public static Specification<Material> hasIds(List<Long> ids) {
        return fieldIn("id", ids);
    }

    public static Specification<Material> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return dateBetween("createdAt", startDate, endDate);
    }

    public static Specification<Material> createdBy(String createdBy) {
        return fieldEquals("createdBy", createdBy);
    }

    public static Specification<Material> nameStartsWith(String prefix) {
        return (root, query, cb) -> {
            if (prefix == null || prefix.trim().isEmpty()) return cb.conjunction();
            return cb.like(cb.lower(root.get("name")), prefix.toLowerCase() + "%");
        };
    }

    public static Specification<Material> nameEndsWith(String suffix) {
        return (root, query, cb) -> {
            if (suffix == null || suffix.trim().isEmpty()) return cb.conjunction();
            return cb.like(cb.lower(root.get("name")), "%" + suffix.toLowerCase());
        };
    }

    /**
     * Search across multiple fields
     */
    public static Specification<Material> searchByKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) return cb.conjunction();

            String likePattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("unit")), likePattern)
            );
        };
    }

    public static Specification<Material> searchByMultipleNames(List<String> names) {
        return (root, query, cb) -> {
            if (names == null || names.isEmpty()) return cb.conjunction();

            // Create OR conditions for each name (partial match)
            List<Predicate> namePredicates = new ArrayList<>();
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    namePredicates.add(
                            cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
                    );
                }
            }

            return namePredicates.isEmpty() ?
                    cb.conjunction() :
                    cb.or(namePredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Material> hasExactNames(List<String> names) {
        return (root, query, cb) -> {
            if (names == null || names.isEmpty()) return cb.conjunction();

            // For exact name matches
            List<String> cleanNames = names.stream()
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            return cleanNames.isEmpty() ?
                    cb.conjunction() :
                    root.get("name").in(cleanNames);
        };
    }

}