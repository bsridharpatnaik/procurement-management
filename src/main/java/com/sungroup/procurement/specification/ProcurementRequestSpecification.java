package com.sungroup.procurement.specification;

import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.enums.Priority;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.util.SecurityUtil;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ProcurementRequestSpecification extends BaseSpecification<ProcurementRequest> {

    public static Specification<ProcurementRequest> isNotDeleted() {
        return BaseSpecification.isNotDeleted();
    }

    /**
     * CRITICAL: Factory-based access control specification
     * This should be applied to ALL procurement request queries for factory users
     */
    public static Specification<ProcurementRequest> withFactoryAccessControl() {
        return (root, query, cb) -> {
            List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();

            // If empty list returned, it means no restriction (admin/purchase team/management)
            if (accessibleFactoryIds.isEmpty() && !SecurityUtil.isCurrentUserFactoryUser()) {
                return cb.conjunction(); // No restriction
            }

            // If empty list for factory user, it means no access
            if (accessibleFactoryIds.isEmpty() && SecurityUtil.isCurrentUserFactoryUser()) {
                return cb.disjunction(); // No access
            }

            // Restrict to accessible factories
            return root.get("factory").get("id").in(accessibleFactoryIds);
        };
    }

    public static Specification<ProcurementRequest> hasRequestNumber(String requestNumber) {
        return fieldContains("requestNumber", requestNumber);
    }

    public static Specification<ProcurementRequest> hasStatus(ProcurementStatus status) {
        return fieldEquals("status", status);
    }

    public static Specification<ProcurementRequest> hasStatuses(List<ProcurementStatus> statuses) {
        return fieldIn("status", statuses);
    }

    public static Specification<ProcurementRequest> hasPriority(Priority priority) {
        return fieldEquals("priority", priority);
    }

    public static Specification<ProcurementRequest> hasPriorities(List<Priority> priorities) {
        return fieldIn("priority", priorities);
    }

    public static Specification<ProcurementRequest> hasFactoryId(Long factoryId) {
        return nestedFieldEquals("factory", "id", factoryId);
    }

    public static Specification<ProcurementRequest> hasFactoryIds(List<Long> factoryIds) {
        return nestedFieldIn("factory", "id", factoryIds);
    }

    public static Specification<ProcurementRequest> hasFactoryName(String factoryName) {
        return (root, query, cb) -> {
            if (factoryName == null || factoryName.trim().isEmpty()) return cb.conjunction();
            Join<Object, Object> factoryJoin = root.join("factory", JoinType.INNER);
            return cb.like(cb.lower(factoryJoin.get("name")), "%" + factoryName.toLowerCase() + "%");
        };
    }

    public static Specification<ProcurementRequest> isAssignedToUser(Long userId) {
        return nestedFieldEquals("assignedTo", "id", userId);
    }

    public static Specification<ProcurementRequest> isAssignedToUsers(List<Long> userIds) {
        return nestedFieldIn("assignedTo", "id", userIds);
    }

    public static Specification<ProcurementRequest> isUnassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedTo"));
    }

    public static Specification<ProcurementRequest> requiresApproval(Boolean requiresApproval) {
        return booleanEquals("requiresApproval", requiresApproval);
    }

    public static Specification<ProcurementRequest> isApprovedBy(Long userId) {
        return nestedFieldEquals("approvedBy", "id", userId);
    }

    public static Specification<ProcurementRequest> isShortClosed(Boolean isShortClosed) {
        return booleanEquals("isShortClosed", isShortClosed);
    }

    public static Specification<ProcurementRequest> expectedDeliveryBetween(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) return cb.conjunction();

            if (startDate != null && endDate != null) {
                return cb.between(root.get("expectedDeliveryDate"), startDate, endDate);
            } else if (startDate != null) {
                return cb.greaterThanOrEqualTo(root.get("expectedDeliveryDate"), startDate);
            } else {
                return cb.lessThanOrEqualTo(root.get("expectedDeliveryDate"), endDate);
            }
        };
    }

    public static Specification<ProcurementRequest> approvedBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) return cb.conjunction();

            if (startDate != null && endDate != null) {
                return cb.between(root.get("approvedDate"), startDate, endDate);
            } else if (startDate != null) {
                return cb.greaterThanOrEqualTo(root.get("approvedDate"), startDate);
            } else {
                return cb.lessThanOrEqualTo(root.get("approvedDate"), endDate);
            }
        };
    }

    public static Specification<ProcurementRequest> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return dateBetween("createdAt", startDate, endDate);
    }

    public static Specification<ProcurementRequest> createdBy(String createdBy) {
        return fieldEquals("createdBy", createdBy);
    }

    public static Specification<ProcurementRequest> hasMaterialId(Long materialId) {
        return (root, query, cb) -> {
            if (materialId == null) return cb.conjunction();
            Join<Object, Object> lineItemsJoin = root.join("lineItems", JoinType.INNER);
            Join<Object, Object> materialJoin = lineItemsJoin.join("material", JoinType.INNER);
            query.distinct(true);
            return cb.equal(materialJoin.get("id"), materialId);
        };
    }

    public static Specification<ProcurementRequest> hasMaterialName(String materialName) {
        return (root, query, cb) -> {
            if (materialName == null || materialName.trim().isEmpty()) return cb.conjunction();
            Join<Object, Object> lineItemsJoin = root.join("lineItems", JoinType.INNER);
            Join<Object, Object> materialJoin = lineItemsJoin.join("material", JoinType.INNER);
            query.distinct(true);
            return cb.like(cb.lower(materialJoin.get("name")), "%" + materialName.toLowerCase() + "%");
        };
    }

    public static Specification<ProcurementRequest> hasVendorId(Long vendorId) {
        return (root, query, cb) -> {
            if (vendorId == null) return cb.conjunction();
            Join<Object, Object> lineItemsJoin = root.join("lineItems", JoinType.INNER);
            Join<Object, Object> vendorJoin = lineItemsJoin.join("assignedVendor", JoinType.INNER);
            query.distinct(true);
            return cb.equal(vendorJoin.get("id"), vendorId);
        };
    }

    /**
     * Factory access filter - requests can only be seen by users from assigned factories
     * @deprecated Use withFactoryAccessControl() instead for better security
     */
    @Deprecated
    public static Specification<ProcurementRequest> accessibleByFactories(List<Long> accessibleFactoryIds) {
        return (root, query, cb) -> {
            if (accessibleFactoryIds == null || accessibleFactoryIds.isEmpty()) {
                return cb.disjunction(); // No access
            }
            return root.get("factory").get("id").in(accessibleFactoryIds);
        };
    }

    /**
     * Pending days filter - requests older than specified days
     */
    public static Specification<ProcurementRequest> pendingForDays(int days) {
        return (root, query, cb) -> {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
            return cb.lessThanOrEqualTo(root.get("createdAt"), cutoffDate);
        };
    }

    /**
     * Combined specification that MUST be used for all procurement request queries
     * to ensure proper access control
     */
    public static Specification<ProcurementRequest> withSecurityAndNotDeleted() {
        return isNotDeleted().and(withFactoryAccessControl());
    }
}