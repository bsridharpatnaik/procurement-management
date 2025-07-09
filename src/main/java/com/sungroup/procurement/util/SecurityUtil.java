package com.sungroup.procurement.util;

import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.security.UserDetailsServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for security-related operations and access control
 */
@Component
@Slf4j
public class SecurityUtil {

    /**
     * Get the currently authenticated user
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsServiceImpl.UserPrincipal) {
            UserDetailsServiceImpl.UserPrincipal userPrincipal =
                    (UserDetailsServiceImpl.UserPrincipal) authentication.getPrincipal();
            return userPrincipal.getUser();
        }
        return null;
    }

    /**
     * Get the current user's username
     */
    public static String getCurrentUsername() {
        User currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getUsername() : null;
    }

    /**
     * Get the current user's role
     */
    public static UserRole getCurrentUserRole() {
        User currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getRole() : null;
    }

    /**
     * Check if current user is factory user
     */
    public static boolean isCurrentUserFactoryUser() {
        return getCurrentUserRole() == UserRole.FACTORY_USER;
    }

    /**
     * Check if current user is purchase team or management
     */
    public static boolean isCurrentUserPurchaseTeamOrManagement() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM ||
                role == UserRole.MANAGEMENT ||
                role == UserRole.ADMIN;
    }

    /**
     * Check if current user is management
     */
    public static boolean isCurrentUserManagement() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.MANAGEMENT || role == UserRole.ADMIN;
    }

    /**
     * Check if current user is admin
     */
    public static boolean isCurrentUserAdmin() {
        return getCurrentUserRole() == UserRole.ADMIN;
    }

    /**
     * Get factory IDs that current user has access to
     * - Factory users: Only their assigned factories
     * - Purchase team/Management/Admin: All factories (return empty list to indicate no restriction)
     */
    public static List<Long> getCurrentUserAccessibleFactoryIds() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return Collections.emptyList();
        }

        // Factory users can only see their assigned factories
        if (currentUser.getRole() == UserRole.FACTORY_USER) {
            Set<Factory> assignedFactories = currentUser.getAssignedFactories();
            if (assignedFactories != null && !assignedFactories.isEmpty()) {
                return assignedFactories.stream()
                        .map(Factory::getId)
                        .collect(Collectors.toList());
            }
            // If no factories assigned, return empty list (no access)
            return Collections.emptyList();
        }

        // Purchase team, Management, and Admin can see all factories
        // Return empty list to indicate no restriction
        return Collections.emptyList();
    }

    /**
     * Check if current user has access to a specific factory
     */
    public static boolean hasAccessToFactory(Long factoryId) {
        if (factoryId == null) {
            return false;
        }

        User currentUser = getCurrentUser();
        if (currentUser == null) {
            log.warn("Factory access check failed: No authenticated user");
            return false;
        }

        // Admin, Purchase team, and Management have access to all factories
        if (currentUser.getRole() != UserRole.FACTORY_USER) {
            return true;
        }

        // Factory users can only access their assigned factories
        Set<Factory> assignedFactories = currentUser.getAssignedFactories();
        if (assignedFactories != null) {
            return assignedFactories.stream()
                    .anyMatch(factory -> factory.getId().equals(factoryId));
        }

        log.warn("Factory access denied: User {} has no assigned factories", currentUser.getUsername());
        return false;
    }

    /**
     * Check if current user can edit a procurement request based on its factory
     */
    public static boolean canAccessProcurementRequest(Long factoryId) {
        return hasAccessToFactory(factoryId);
    }

    /**
     * Validate if current user has permission to perform an action
     */
    public static void validateFactoryAccess(Long factoryId, String operation) {
        if (!hasAccessToFactory(factoryId)) {
            User currentUser = getCurrentUser();
            String username = currentUser != null ? currentUser.getUsername() : "unknown";
            UserRole role = currentUser != null ? currentUser.getRole() : null;

            log.warn("Access denied: User {} (role: {}) attempted to {} for factory ID {}",
                    username, role, operation, factoryId);

            throw new SecurityException("Access denied: You don't have permission to access this factory's data");
        }
    }

    /**
     * Check if current user can see vendor information
     */
    public static boolean canSeeVendorInformation() {
        UserRole role = getCurrentUserRole();
        boolean canSee = role != UserRole.FACTORY_USER;

        if (!canSee) {
            // Log when vendor information access is denied (for audit purposes)
            logSecurityEvent("VENDOR_INFO_ACCESS_DENIED",
                    "Factory user attempted to access vendor information");
        }

        return canSee;
    }

    /**
     * Check if current user can assign procurement requests
     */
    public static boolean canAssignProcurementRequests() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM || role == UserRole.MANAGEMENT || role == UserRole.ADMIN;
    }

    /**
     * Check if current user can approve procurement requests
     */
    public static boolean canApproveProcurementRequests() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.MANAGEMENT || role == UserRole.ADMIN;
    }

    /**
     * Check if current user can be assigned to procurement requests
     */
    public static boolean canBeAssignedToProcurementRequests(User user) {
        if (user == null) {
            return false;
        }
        // FIXED: Also check if user is active
        return user.getRole() == UserRole.PURCHASE_TEAM && user.getIsActive();
    }

    /**
     * Check if current user can set approval flag
     */
    public static boolean canSetApprovalFlag() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM || role == UserRole.MANAGEMENT || role == UserRole.ADMIN;
    }

    /**
     * Get current user's factory ID (for factory users)
     * Returns null if user is not factory user or has multiple factories
     */
    public static Long getCurrentUserFactoryId() {
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getRole() != UserRole.FACTORY_USER) {
            return null;
        }

        Set<Factory> factories = currentUser.getAssignedFactories();
        if (factories != null && factories.size() == 1) {
            return factories.iterator().next().getId();
        }

        return null;
    }

    // NEW: Comprehensive factory access validation with detailed logging
    public static void validateFactoryAccessWithContext(Long factoryId, String operation, String resourceType, Long resourceId) {
        if (!hasAccessToFactory(factoryId)) {
            User currentUser = getCurrentUser();
            String username = currentUser != null ? currentUser.getUsername() : "unknown";
            UserRole role = currentUser != null ? currentUser.getRole() : null;

            log.error("SECURITY VIOLATION: User {} (role: {}) attempted unauthorized {} on {} ID {} for factory ID {}",
                    username, role, operation, resourceType, resourceId, factoryId);

            throw new SecurityException(String.format(
                    "Access denied: You don't have permission to %s %s for this factory",
                    operation, resourceType));
        }
    }

    // NEW: Get factory filter specification for repository queries
    public static List<Long> getFactoryFilterForCurrentUser() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return Collections.emptyList(); // No access
        }

        // Factory users get only their assigned factories
        if (currentUser.getRole() == UserRole.FACTORY_USER) {
            Set<Factory> assignedFactories = currentUser.getAssignedFactories();
            if (assignedFactories != null && !assignedFactories.isEmpty()) {
                return assignedFactories.stream()
                        .map(Factory::getId)
                        .collect(Collectors.toList());
            }
            return Collections.emptyList(); // No access if no factories assigned
        }

        // Non-factory users: return null to indicate no filtering needed (access to all)
        return null;
    }

    // NEW: Validate if user can access multiple factories
    public static void validateMultiFactoryAccess(List<Long> factoryIds, String operation) {
        if (factoryIds == null || factoryIds.isEmpty()) {
            return;
        }

        for (Long factoryId : factoryIds) {
            validateFactoryAccess(factoryId, operation);
        }
    }

    // NEW: More specific role validation methods
    public static boolean canCreateProcurementRequests() {
        return getCurrentUserRole() == UserRole.FACTORY_USER;
    }

    public static boolean canProcessProcurementRequests() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM ||
                role == UserRole.MANAGEMENT ||
                role == UserRole.ADMIN;
    }

    public static boolean canManageUsers() {
        return getCurrentUserRole() == UserRole.ADMIN;
    }

    public static boolean canManageVendors() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM ||
                role == UserRole.MANAGEMENT ||
                role == UserRole.ADMIN;
    }

    public static boolean canManageMaterials() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.FACTORY_USER ||
                role == UserRole.PURCHASE_TEAM ||
                role == UserRole.MANAGEMENT ||
                role == UserRole.ADMIN;
    }

    // NEW: Enhanced assignment validation
    public static void validateAssignmentTarget(User assignee, ProcurementRequest request) {
        if (assignee == null) {
            throw new ValidationException("Assignment target user is required");
        }

        if (!canBeAssignedToProcurementRequests(assignee)) {
            throw new ValidationException("Only active purchase team members can be assigned to procurement requests");
        }

        // Additional validation: assignee should have access to the request's factory
        if (assignee.getRole() == UserRole.FACTORY_USER) {
            // This shouldn't happen as factory users can't be assigned, but adding safety check
            throw new ValidationException("Factory users cannot be assigned to procurement requests");
        }
    }

    // NEW: Data isolation enforcement methods
    public static void enforceFactoryDataIsolation(Object entity, String operation) {
        if (entity == null) return;

        // Extract factory ID from different entity types
        Long factoryId = null;
        String entityType = entity.getClass().getSimpleName();

        if (entity instanceof ProcurementRequest) {
            factoryId = ((ProcurementRequest) entity).getFactory().getId();
        } else if (entity instanceof ProcurementLineItem) {
            factoryId = ((ProcurementLineItem) entity).getProcurementRequest().getFactory().getId();
        }
        // Add more entity types as needed

        if (factoryId != null) {
            validateFactoryAccessWithContext(factoryId, operation, entityType, getEntityId(entity));
        }
    }

    // NEW: Helper method to extract entity ID for logging
    private static Long getEntityId(Object entity) {
        try {
            // Use reflection to get ID field
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return (Long) idField.get(entity);
        } catch (Exception e) {
            return null; // ID extraction failed, not critical for security
        }
    }

    // NEW: Session and audit methods
    public static void logSecurityEvent(String event, String details) {
        User currentUser = getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        UserRole role = currentUser != null ? currentUser.getRole() : null;

        log.info("SECURITY EVENT: {} - User: {} ({}), Details: {}",
                event, username, role, details);
    }

    public static void logAccessViolation(String operation, String resource, String reason) {
        User currentUser = getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        UserRole role = currentUser != null ? currentUser.getRole() : null;

        log.warn("ACCESS VIOLATION: {} attempted {} on {} - Reason: {}",
                username + "(" + role + ")", operation, resource, reason);
    }

    // NEW: Factory user specific validations
    public static void validateFactoryUserCanOnlyAccessOwnData(Long factoryId) {
        if (!isCurrentUserFactoryUser()) {
            return; // Non-factory users are not restricted
        }

        validateFactoryAccess(factoryId, "access factory data");
    }

    public static List<Long> getRestrictedFactoryIds() {
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getRole() != UserRole.FACTORY_USER) {
            return null; // No restrictions for non-factory users
        }

        return getCurrentUserAccessibleFactoryIds();
    }

    // NEW: Comprehensive permission checking
    public static void validateUserHasPermission(String permission, Object context) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new SecurityException("Authentication required");
        }

        switch (permission.toUpperCase()) {
            case "CREATE_PROCUREMENT_REQUEST":
                if (!canCreateProcurementRequests()) {
                    throw new SecurityException("Only factory users can create procurement requests");
                }
                break;

            case "PROCESS_PROCUREMENT_REQUEST":
                if (!canProcessProcurementRequests()) {
                    throw new SecurityException("Only purchase team and management can process procurement requests");
                }
                break;

            case "MANAGE_VENDORS":
                if (!canManageVendors()) {
                    throw new SecurityException("Only purchase team and management can manage vendors");
                }
                break;

            case "VIEW_VENDOR_INFORMATION":
                if (!canSeeVendorInformation()) {
                    throw new SecurityException("Factory users cannot view vendor information");
                }
                break;

            case "APPROVE_REQUESTS":
                if (!canApproveProcurementRequests()) {
                    throw new SecurityException("Only management can approve requests");
                }
                break;

            default:
                throw new SecurityException("Unknown permission: " + permission);
        }
    }

    // NEW: Method to check if current user can access cross-factory data
    public static boolean canAccessCrossFactoryData() {
        UserRole role = getCurrentUserRole();
        return role != UserRole.FACTORY_USER;
    }

    // NEW: Validate factory assignment for factory users
    public static void validateFactoryUserHasAssignedFactories() {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == UserRole.FACTORY_USER) {
            Set<Factory> assignedFactories = currentUser.getAssignedFactories();
            if (assignedFactories == null || assignedFactories.isEmpty()) {
                throw new SecurityException("Factory user must be assigned to at least one factory");
            }
        }
    }
}