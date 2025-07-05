package com.sungroup.procurement.specification;

import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.List;

public class UserSpecification extends BaseSpecification<User> {

    public static Specification<User> isNotDeleted() {
        return BaseSpecification.isNotDeleted();
    }

    public static Specification<User> hasUsername(String username) {
        return fieldContains("username", username);
    }

    public static Specification<User> hasFullName(String fullName) {
        return fieldContains("fullName", fullName);
    }

    public static Specification<User> hasEmail(String email) {
        return fieldContains("email", email);
    }

    public static Specification<User> hasRole(UserRole role) {
        return fieldEquals("role", role);
    }

    public static Specification<User> hasRoles(List<UserRole> roles) {
        return fieldIn("role", roles);
    }

    public static Specification<User> isActive(Boolean active) {
        return booleanEquals("isActive", active);
    }

    public static Specification<User> hasFactoryId(Long factoryId) {
        return (root, query, cb) -> {
            if (factoryId == null) return cb.conjunction();
            Join<Object, Object> factoryJoin = root.join("assignedFactories", JoinType.INNER);
            return cb.equal(factoryJoin.get("id"), factoryId);
        };
    }

    public static Specification<User> hasFactoryIds(List<Long> factoryIds) {
        return (root, query, cb) -> {
            if (factoryIds == null || factoryIds.isEmpty()) return cb.conjunction();
            Join<Object, Object> factoryJoin = root.join("assignedFactories", JoinType.INNER);
            query.distinct(true);
            return factoryJoin.get("id").in(factoryIds);
        };
    }

    public static Specification<User> hasFactoryName(String factoryName) {
        return (root, query, cb) -> {
            if (factoryName == null || factoryName.trim().isEmpty()) return cb.conjunction();
            Join<Object, Object> factoryJoin = root.join("assignedFactories", JoinType.INNER);
            return cb.like(cb.lower(factoryJoin.get("name")), "%" + factoryName.toLowerCase() + "%");
        };
    }

    public static Specification<User> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return dateBetween("createdAt", startDate, endDate);
    }

    public static Specification<User> createdBy(String createdBy) {
        return fieldEquals("createdBy", createdBy);
    }

    /**
     * Factory access filter - users can only see users from their assigned factories
     */
    public static Specification<User> accessibleByFactories(List<Long> accessibleFactoryIds) {
        return (root, query, cb) -> {
            if (accessibleFactoryIds == null || accessibleFactoryIds.isEmpty()) {
                return cb.disjunction(); // No access
            }

            // Admin and Management can see all users
            // Factory users can only see users from their factories
            Join<Object, Object> factoryJoin = root.join("assignedFactories", JoinType.LEFT);
            query.distinct(true);

            return cb.or(
                    // Users without factory assignments (Admin, Purchase Team, Management)
                    cb.isEmpty(root.get("assignedFactories")),
                    // Users with accessible factory assignments
                    factoryJoin.get("id").in(accessibleFactoryIds)
            );
        };
    }
}