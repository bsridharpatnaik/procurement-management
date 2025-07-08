package com.sungroup.procurement.util;

import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
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
        return role == UserRole.PURCHASE_TEAM || role == UserRole.MANAGEMENT;
    }

    /**
     * Check if current user is management
     */
    public static boolean isCurrentUserManagement() {
        return getCurrentUserRole() == UserRole.MANAGEMENT;
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
            String username = getCurrentUsername();
            log.warn("Access denied: User {} attempted to {} for factory ID {}",
                    username, operation, factoryId);
            throw new SecurityException("Access denied: You don't have permission to access this factory's data");
        }
    }

    /**
     * Check if current user can see vendor information
     */
    public static boolean canSeeVendorInformation() {
        UserRole role = getCurrentUserRole();
        return role != UserRole.FACTORY_USER;
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
        return user.getRole() == UserRole.PURCHASE_TEAM;
    }

    /**
     * Check if current user can set approval flag
     */
    public static boolean canSetApprovalFlag() {
        UserRole role = getCurrentUserRole();
        return role == UserRole.PURCHASE_TEAM || role == UserRole.MANAGEMENT || role == UserRole.ADMIN;
    }
}