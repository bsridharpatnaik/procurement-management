package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.request.PasswordUpdateRequest;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.*;
import com.sungroup.procurement.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FactoryRepository factoryRepository;
    private final FilterService filterService;
    private final PasswordEncoder passwordEncoder;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final MaterialRepository materialRepository;
    private final VendorRepository vendorRepository;

    // READ Operations
    public ApiResponse<List<User>> findUsersWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<User> spec = buildUserSpecification(filterData);
            Page<User> userPage = userRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(userPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, userPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching users with filters", e);
            return ApiResponse.error("Failed to fetch users: " + e.getMessage());
        }
    }

    public ApiResponse<User> findById(Long id) {
        try {
            User user = userRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, user);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching user by id: {}", id, e);
            return ApiResponse.error("Failed to fetch user");
        }
    }

    public ApiResponse<User> findByUsername(String username) {
        try {
            User user = userRepository.findByUsernameAndIsDeletedFalse(username)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, user);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching user by username: {}", username, e);
            return ApiResponse.error("Failed to fetch user");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<User> createUser(User user) {
        try {
            validateUserForCreate(user);

            // Encode password
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            // Set assigned factories if provided
            if (user.getAssignedFactories() != null && !user.getAssignedFactories().isEmpty()) {
                Set<Factory> validFactories = validateAndGetFactories(user.getAssignedFactories());
                user.setAssignedFactories(validFactories);
            }

            User savedUser = userRepository.save(user);
            log.info("User created successfully: {}", savedUser.getUsername());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedUser);
        } catch (DuplicateEntityException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating user", e);
            return ApiResponse.error("Failed to create user");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<User> updateUser(Long id, User userDetails) {
        try {
            User existingUser = userRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // PREVENT USERNAME UPDATES
            if (userDetails.getUsername() != null &&
                    !userDetails.getUsername().equals(existingUser.getUsername())) {
                throw new ValidationException("Username cannot be updated");
            }

            // PREVENT ROLE CHANGES FOR USERS IN USE
            if (userDetails.getRole() != null &&
                    userDetails.getRole() != existingUser.getRole()) {

                ApiResponse<String> roleChangeCheck = validateRoleChange(existingUser, userDetails.getRole());
                if (!roleChangeCheck.isSuccess()) {
                    return ApiResponse.error(roleChangeCheck.getMessage());
                }
            }

            // VALIDATE OTHER FIELDS
            validateUserForUpdate(userDetails, existingUser);

            // UPDATE ALLOWED FIELDS
            updateUserFields(existingUser, userDetails);

            User updatedUser = userRepository.save(existingUser);
            log.info("User updated successfully: {}", updatedUser.getUsername());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedUser);
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating user with id: {}", id, e);
            return ApiResponse.error("Failed to update user");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteUser(Long id) {
        try {
            User user = userRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // PREVENT SPECIAL DELETIONS
            ApiResponse<String> preventionCheck = preventSpecialUserDeletion(user);
            if (!preventionCheck.isSuccess()) {
                return preventionCheck;
            }

            // CHECK ALL DEPENDENCIES (including materials/vendors)
            List<String> dependencies = checkUserDependencies(user);

            if (!dependencies.isEmpty()) {
                String message = "Cannot delete user '" + user.getUsername() +
                        "'. User is associated with: " + String.join("; ", dependencies);
                return ApiResponse.error(message);
            }

            // SOFT DELETE IF NO DEPENDENCIES
            user.setIsDeleted(true);
            userRepository.save(user);
            log.info("User soft deleted successfully: {}", user.getUsername());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS,
                    "User '" + user.getUsername() + "' deleted successfully");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting user with id: {}", id, e);
            return ApiResponse.error("Failed to delete user");
        }
    }

    // UTILITY Methods
    private Specification<User> buildUserSpecification(FilterDataList filterData) {
        Specification<User> spec = UserSpecification.isNotDeleted();

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        String username = filterService.getStringValue(filterData, "username");
        String fullName = filterService.getStringValue(filterData, "fullName");
        String email = filterService.getStringValue(filterData, "email");
        List<UserRole> roles = filterService.getUserRoleValues(filterData, "role");
        Boolean isActive = filterService.getBooleanValue(filterData, "isActive");
        List<Long> factoryIds = filterService.getLongValues(filterData, "factoryId");
        String factoryName = filterService.getStringValue(filterData, "factoryName");
        String createdBy = filterService.getStringValue(filterData, "createdBy");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        if (username != null) spec = spec.and(UserSpecification.hasUsername(username));
        if (fullName != null) spec = spec.and(UserSpecification.hasFullName(fullName));
        if (email != null) spec = spec.and(UserSpecification.hasEmail(email));
        if (roles != null && !roles.isEmpty()) spec = spec.and(UserSpecification.hasRoles(roles));
        if (isActive != null) spec = spec.and(UserSpecification.isActive(isActive));
        if (factoryIds != null && !factoryIds.isEmpty()) spec = spec.and(UserSpecification.hasFactoryIds(factoryIds));
        if (factoryName != null) spec = spec.and(UserSpecification.hasFactoryName(factoryName));
        if (createdBy != null) spec = spec.and(UserSpecification.createdBy(createdBy));
        if (createdDateRange != null) {
            spec = spec.and(UserSpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        return spec;
    }

    private void validateUserForCreate(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new ValidationException("Username is required");
        }

        // Trim username before other validations
        String username = user.getUsername().trim();
        user.setUsername(username); // Set trimmed username

        // Check for spaces
        if (username.contains(" ")) {
            throw new ValidationException("Username cannot contain spaces");
        }

        // Check minimum length
        if (username.length() < 3) {
            throw new ValidationException("Username must be at least 3 characters long");
        }

        // Check maximum length
        if (username.length() > 50) {
            throw new ValidationException("Username cannot exceed 50 characters");
        }

        // Check valid characters (only letters, numbers, underscore, dot, hyphen)
        if (!username.matches("^[a-zA-Z0-9._-]+$")) {
            throw new ValidationException("Username can only contain letters, numbers, underscore, dot, and hyphen");
        }

        // Check it doesn't start/end with special characters
        if (username.startsWith(".") || username.startsWith("-") || username.startsWith("_") ||
                username.endsWith(".") || username.endsWith("-") || username.endsWith("_")) {
            throw new ValidationException("Username cannot start or end with special characters");
        }

        // Check other required fields
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new ValidationException("Password is required");
        }
        if (user.getFullName() == null || user.getFullName().trim().isEmpty()) {
            throw new ValidationException("Full name is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        if (user.getRole() == null) {
            throw new ValidationException("Role is required");
        }

        // Check for duplicates - do this ONCE at the end
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateEntityException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEntityException("Email already exists");
        }
    }

    private void validateUserForUpdate(User userDetails, User existingUser) {
        // Validate fullName if provided
        if (userDetails.getFullName() != null) {
            if (userDetails.getFullName().trim().isEmpty()) {
                throw new ValidationException("Full name cannot be empty");
            }
        }

        // Validate email if provided
        if (userDetails.getEmail() != null) {
            if (userDetails.getEmail().trim().isEmpty()) {
                throw new ValidationException("Email cannot be empty");
            }
            // Check for duplicates only if email is changing
            if (!userDetails.getEmail().equals(existingUser.getEmail())) {
                if (userRepository.existsByEmail(userDetails.getEmail())) {
                    throw new DuplicateEntityException("Email already exists");
                }
            }
        }

        // Validate role if provided
        if (userDetails.getRole() == null) {
            throw new ValidationException("Role is required");
        }
    }


    private Set<Factory> validateAndGetFactories(Set<Factory> factories) {
        Set<Factory> validFactories = new HashSet<>();
        for (Factory factory : factories) {
            Factory existingFactory = factoryRepository.findByIdAndIsDeletedFalse(factory.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Factory not found with id: " + factory.getId()));
            validFactories.add(existingFactory);
        }
        return validFactories;
    }

    @Transactional
    public ApiResponse<String> updatePassword(Long userId, PasswordUpdateRequest request) {
        try {
            User user = userRepository.findByIdAndIsDeletedFalse(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            validatePasswordUpdate(request, user);

            // Update password
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            log.info("Password updated successfully for user: {}", user.getUsername());
            return ApiResponse.success("Password updated successfully", "Password has been changed");
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating password for user id: {}", userId, e);
            return ApiResponse.error("Failed to update password");
        }
    }

    private void validatePasswordUpdate(PasswordUpdateRequest request, User user) {
        // Check if current password matches
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ValidationException("Current password is incorrect");
        }

        // Check if new password and confirm password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("New password and confirm password do not match");
        }

        // Check if new password is different from current
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ValidationException("New password must be different from current password");
        }

        // Additional password strength validation
        validatePasswordStrength(request.getNewPassword());
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("Password cannot be empty");
        }

        if (password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters long");
        }

        if (password.length() > 100) {
            throw new ValidationException("Password cannot exceed 100 characters");
        }
    }

    private ApiResponse<String> validateRoleChange(User user, UserRole newRole) {
        // Check if user has any dependencies
        List<String> dependencies = checkUserDependencies(user);

        if (!dependencies.isEmpty()) {
            String message = "Cannot change role for user '" + user.getUsername() +
                    "' from " + user.getRole() + " to " + newRole +
                    ". User is associated with: " + String.join("; ", dependencies);
            return ApiResponse.error(message);
        }

        // Additional role-specific validations
        return validateSpecificRoleChange(user, newRole);
    }

    // Special user deletion prevention
    private ApiResponse<String> preventSpecialUserDeletion(User user) {
        // Prevent deletion of admin user
        if ("admin".equals(user.getUsername().toLowerCase())) {
            return ApiResponse.error("Cannot delete the system admin user");
        }

        // Prevent self-deletion
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && user.getUsername().equals(authentication.getName())) {
            return ApiResponse.error("Cannot delete your own user account");
        }

        // Prevent deletion of last admin user
        if (user.getRole() == UserRole.ADMIN) {
            long activeAdminCount = userRepository.countByRoleAndIsDeletedFalseAndIsActiveTrue(UserRole.ADMIN);
            if (activeAdminCount <= 1) {
                return ApiResponse.error("Cannot delete the last active admin user");
            }
        }

        return ApiResponse.success("", "Validation passed");
    }

    // Comprehensive dependency checks (including materials/vendors)
    private List<String> checkUserDependencies(User user) {
        List<String> dependencies = new ArrayList<>();

        // Procurement Requests - Created by user
        long createdRequestsCount = procurementRequestRepository.countByCreatedByAndIsDeletedFalse(user.getUsername());
        if (createdRequestsCount > 0) {
            dependencies.add("Created Procurement Requests (" + createdRequestsCount + ")");
        }

        // Procurement Requests - Assigned to user
        long assignedRequestsCount = procurementRequestRepository.countByAssignedToIdAndIsDeletedFalse(user.getId());
        if (assignedRequestsCount > 0) {
            dependencies.add("Assigned Procurement Requests (" + assignedRequestsCount + ")");
        }

        // Procurement Requests - Approved by user
        long approvedRequestsCount = procurementRequestRepository.countByApprovedByIdAndIsDeletedFalse(user.getId());
        if (approvedRequestsCount > 0) {
            dependencies.add("Approved Procurement Requests (" + approvedRequestsCount + ")");
        }

        // NEW: Materials - Created by user
        try {
            long createdMaterialsCount = materialRepository.countByCreatedByAndIsDeletedFalse(user.getUsername());
            if (createdMaterialsCount > 0) {
                dependencies.add("Created Materials (" + createdMaterialsCount + ")");
            }
        } catch (Exception e) {
            log.debug("Material dependency check failed: {}", e.getMessage());
        }

        // NEW: Vendors - Created by user
        try {
            long createdVendorsCount = vendorRepository.countByCreatedByAndIsDeletedFalse(user.getUsername());
            if (createdVendorsCount > 0) {
                dependencies.add("Created Vendors (" + createdVendorsCount + ")");
            }
        } catch (Exception e) {
            log.debug("Vendor dependency check failed: {}", e.getMessage());
        }

        return dependencies;
    }

    // Role-specific change validation
    private ApiResponse<String> validateSpecificRoleChange(User user, UserRole newRole) {
        // Prevent downgrading last admin
        if (user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long activeAdminCount = userRepository.countByRoleAndIsDeletedFalseAndIsActiveTrue(UserRole.ADMIN);
            if (activeAdminCount <= 1) {
                return ApiResponse.error("Cannot change role of the last active admin user");
            }
        }

        // Prevent changing your own role to lower privilege
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && user.getUsername().equals(authentication.getName())) {
            if (isRoleDowngrade(user.getRole(), newRole)) {
                return ApiResponse.error("Cannot downgrade your own role");
            }
        }

        return ApiResponse.success("", "Role change validation passed");
    }

    // Check if role change is a downgrade
    private boolean isRoleDowngrade(UserRole currentRole, UserRole newRole) {
        // Java 8 compatible way to create role hierarchy map
        Map<UserRole, Integer> roleHierarchy = new HashMap<>();
        roleHierarchy.put(UserRole.ADMIN, 4);
        roleHierarchy.put(UserRole.MANAGEMENT, 3);
        roleHierarchy.put(UserRole.PURCHASE_TEAM, 2);
        roleHierarchy.put(UserRole.FACTORY_USER, 1);
        return roleHierarchy.get(newRole) < roleHierarchy.get(currentRole);
    }

    // Field update helper
    private void updateUserFields(User existingUser, User userDetails) {
        if (userDetails.getFullName() != null) {
            existingUser.setFullName(userDetails.getFullName());
        }
        if (userDetails.getEmail() != null) {
            existingUser.setEmail(userDetails.getEmail());
        }
        if (userDetails.getRole() != null) {
            existingUser.setRole(userDetails.getRole());
        }
        if (userDetails.getIsActive() != null) {
            existingUser.setIsActive(userDetails.getIsActive());
        }
        if (userDetails.getAssignedFactories() != null) {
            Set<Factory> validFactories = validateAndGetFactories(userDetails.getAssignedFactories());
            existingUser.setAssignedFactories(validFactories);
        }

        // Update password if provided
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
    }
}