package com.sungroup.procurement.specification;

import com.sungroup.procurement.entity.Factory;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class FactorySpecification extends BaseSpecification<Factory> {

    public static Specification<Factory> isNotDeleted() {
        return BaseSpecification.isNotDeleted();
    }

    public static Specification<Factory> hasName(String name) {
        return fieldContains("name", name);
    }

    public static Specification<Factory> hasFactoryCode(String factoryCode) {
        return fieldEquals("factoryCode", factoryCode);
    }

    public static Specification<Factory> hasFactoryCodes(List<String> factoryCodes) {
        return fieldIn("factoryCode", factoryCodes);
    }

    public static Specification<Factory> isActive(Boolean active) {
        return booleanEquals("isActive", active);
    }

    public static Specification<Factory> hasIds(List<Long> ids) {
        return fieldIn("id", ids);
    }

    public static Specification<Factory> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return dateBetween("createdAt", startDate, endDate);
    }

    public static Specification<Factory> createdBy(String createdBy) {
        return fieldEquals("createdBy", createdBy);
    }

    /**
     * Factory access filter - users can only see their assigned factories
     */
    public static Specification<Factory> accessibleByUser(List<Long> accessibleFactoryIds) {
        return (root, query, cb) -> {
            if (accessibleFactoryIds == null || accessibleFactoryIds.isEmpty()) {
                return cb.disjunction(); // No access
            }
            return root.get("id").in(accessibleFactoryIds);
        };
    }
}