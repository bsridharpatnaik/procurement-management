package com.sungroup.procurement.specification;

import com.sungroup.procurement.entity.Vendor;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class VendorSpecification extends BaseSpecification<Vendor> {

    public static Specification<Vendor> isNotDeleted() {
        return BaseSpecification.isNotDeleted();
    }

    public static Specification<Vendor> hasName(String name) {
        return fieldContains("name", name);
    }

    public static Specification<Vendor> searchByMultipleNames(List<String> names) {
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

    public static Specification<Vendor> hasContactNumber(String contactNumber) {
        return fieldContains("contactNumber", contactNumber);
    }

    public static Specification<Vendor> hasEmail(String email) {
        return fieldContains("email", email);
    }

    public static Specification<Vendor> hasIds(List<Long> ids) {
        return fieldIn("id", ids);
    }

    public static Specification<Vendor> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return dateBetween("createdAt", startDate, endDate);
    }

    public static Specification<Vendor> createdBy(String createdBy) {
        return fieldEquals("createdBy", createdBy);
    }

    public static Specification<Vendor> contactStartsWith(String prefix) {
        return (root, query, cb) -> {
            if (prefix == null || prefix.trim().isEmpty()) return cb.conjunction();
            return cb.like(root.get("contactNumber"), prefix + "%");
        };
    }

    public static Specification<Vendor> hasValidEmail() {
        return (root, query, cb) -> {
            return cb.and(
                    cb.isNotNull(root.get("email")),
                    cb.notEqual(root.get("email"), ""),
                    cb.like(root.get("email"), "%@%")
            );
        };
    }

    public static Specification<Vendor> hasValidContactNumber() {
        return (root, query, cb) -> {
            return cb.and(
                    cb.isNotNull(root.get("contactNumber")),
                    cb.notEqual(root.get("contactNumber"), "")
            );
        };
    }

    /**
     * Search across multiple fields
     */
    public static Specification<Vendor> searchByKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) return cb.conjunction();

            String likePattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern),
                    cb.like(root.get("contactNumber"), likePattern)
            );
        };
    }
}