package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.FactoryRepository;
import com.sungroup.procurement.repository.ProcurementRequestRepository;
import com.sungroup.procurement.repository.UserRepository;
import com.sungroup.procurement.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FactoryRepository factoryRepository;
    private final FilterService filterService;
    private final PasswordEncoder passwordEncoder;
    private final ProcurementRequestRepository procurementRequestRepository;
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

            validateUserForUpdate(userDetails, existingUser);

            // Update fields
            existingUser.setFullName(userDetails.getFullName());
            existingUser.setEmail(userDetails.getEmail());
            existingUser.setRole(userDetails.getRole());
            existingUser.setIsActive(userDetails.getIsActive());

            // Update password if provided
            if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
            }

            // Update assigned factories
            if (userDetails.getAssignedFactories() != null) {
                Set<Factory> validFactories = validateAndGetFactories(userDetails.getAssignedFactories());
                existingUser.setAssignedFactories(validFactories);
            }

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

            List<String> dependencies = new ArrayList<>();

            // Check created requests - using createdBy as String field
            long createdRequestsCount = procurementRequestRepository.countByCreatedByAndIsDeletedFalse(user.getUsername());
            if (createdRequestsCount > 0) {
                dependencies.add("Created Requests: " + createdRequestsCount + " requests");
            }

            // Check assigned requests - if assignedTo is User entity
            long assignedRequestsCount = procurementRequestRepository.countByAssignedToIdAndIsDeletedFalse(id);
            if (assignedRequestsCount > 0) {
                dependencies.add("Assigned Requests: " + assignedRequestsCount + " requests");
            }

            // Check approved requests - if approvedBy is User entity
            long approvedRequestsCount = procurementRequestRepository.countByApprovedByIdAndIsDeletedFalse(id);
            if (approvedRequestsCount > 0) {
                dependencies.add("Approved Requests: " + approvedRequestsCount + " requests");
            }

            if (!dependencies.isEmpty()) {
                String message = "Cannot delete user '" + user.getUsername() +
                        "'. User is associated with: " + String.join("; ", dependencies);
                return ApiResponse.error(message);
            }

            // Soft delete if no dependencies
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

        // Check for duplicates
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
}