package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.FactoryRepository;
import com.sungroup.procurement.repository.ProcurementRequestRepository;
import com.sungroup.procurement.repository.UserRepository;
import com.sungroup.procurement.specification.FactorySpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FactoryService {

    private final FactoryRepository factoryRepository;
    private final FilterService filterService;

    private final UserRepository userRepository;
    private final ProcurementRequestRepository procurementRequestRepository;

    // READ Operations
    public ApiResponse<List<Factory>> findFactoriesWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<Factory> spec = buildFactorySpecification(filterData);
            Page<Factory> factoryPage = factoryRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(factoryPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, factoryPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching factories with filters", e);
            return ApiResponse.error("Failed to fetch factories: " + e.getMessage());
        }
    }

    public ApiResponse<Factory> findById(Long id) {
        try {
            Factory factory = factoryRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, factory);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching factory by id: {}", id, e);
            return ApiResponse.error("Failed to fetch factory");
        }
    }

    public ApiResponse<Factory> findByFactoryCode(String factoryCode) {
        try {
            Factory factory = factoryRepository.findByFactoryCodeAndIsDeletedFalse(factoryCode)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, factory);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching factory by code: {}", factoryCode, e);
            return ApiResponse.error("Failed to fetch factory");
        }
    }

    public ApiResponse<List<Factory>> findActiveFactories() {
        try {
            Specification<Factory> spec = FactorySpecification.isNotDeleted()
                    .and(FactorySpecification.isActive(true));

            List<Factory> factories = factoryRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, factories);
        } catch (Exception e) {
            log.error("Error fetching active factories", e);
            return ApiResponse.error("Failed to fetch active factories");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<Factory> createFactory(Factory factory) {
        try {
            validateFactoryForCreate(factory);

            Factory savedFactory = factoryRepository.save(factory);
            log.info("Factory created successfully: {}", savedFactory.getName());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedFactory);
        } catch (DuplicateEntityException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating factory", e);
            return ApiResponse.error("Failed to create factory");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<Factory> updateFactory(Long id, Factory factoryDetails) {
        try {
            Factory existingFactory = factoryRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));

            validateFactoryForUpdate(factoryDetails, existingFactory);

            // Update fields
            existingFactory.setName(factoryDetails.getName());
            existingFactory.setFactoryCode(factoryDetails.getFactoryCode());
            existingFactory.setIsActive(factoryDetails.getIsActive());

            Factory updatedFactory = factoryRepository.save(existingFactory);
            log.info("Factory updated successfully: {}", updatedFactory.getName());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedFactory);
        } catch (EntityNotFoundException | ValidationException | DuplicateEntityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating factory with id: {}", id, e);
            return ApiResponse.error("Failed to update factory");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteFactory(Long id) {
        try {
            Factory factory = factoryRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));

            List<String> dependencies = new ArrayList<>();

            // Check users
            List<User> assignedUsers = userRepository.findByAssignedFactoriesIdAndIsDeletedFalse(id);
            if (!assignedUsers.isEmpty()) {
                List<String> usernames = assignedUsers.stream()
                        .map(User::getUsername)
                        .collect(Collectors.toList());
                dependencies.add("Users: " + String.join(", ", usernames));
            }

            // Check procurement requests
            long requestCount = procurementRequestRepository.countByFactoryIdAndIsDeletedFalse(id);
            if (requestCount > 0) {
                dependencies.add("Procurement Requests: " + requestCount + " requests");
            }

            if (!dependencies.isEmpty()) {
                String message = "Cannot delete factory '" + factory.getName() +
                        "'. It is being used in: " + String.join("; ", dependencies);
                return ApiResponse.error(message);
            }

            // Soft delete if no dependencies
            factory.setIsDeleted(true);
            factoryRepository.save(factory);

            log.info("Factory soft deleted successfully: {}", factory.getName());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS,
                    "Factory '" + factory.getName() + "' deleted successfully");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting factory with id: {}", id, e);
            return ApiResponse.error("Failed to delete factory");
        }
    }


    // UTILITY Methods
    private Specification<Factory> buildFactorySpecification(FilterDataList filterData) {
        Specification<Factory> spec = FactorySpecification.isNotDeleted();

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        String name = filterService.getStringValue(filterData, "name");
        String factoryCode = filterService.getStringValue(filterData, "factoryCode");
        List<String> factoryCodes = filterService.getStringValues(filterData, "factoryCodes");
        Boolean isActive = filterService.getBooleanValue(filterData, "isActive");
        List<Long> ids = filterService.getLongValues(filterData, "ids");
        String createdBy = filterService.getStringValue(filterData, "createdBy");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        if (name != null) spec = spec.and(FactorySpecification.hasName(name));
        if (factoryCode != null) spec = spec.and(FactorySpecification.hasFactoryCode(factoryCode));
        if (factoryCodes != null && !factoryCodes.isEmpty()) spec = spec.and(FactorySpecification.hasFactoryCodes(factoryCodes));
        if (isActive != null) spec = spec.and(FactorySpecification.isActive(isActive));
        if (ids != null && !ids.isEmpty()) spec = spec.and(FactorySpecification.hasIds(ids));
        if (createdBy != null) spec = spec.and(FactorySpecification.createdBy(createdBy));
        if (createdDateRange != null) {
            spec = spec.and(FactorySpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        return spec;
    }

    private void validateFactoryForCreate(Factory factory) {
        if (factory.getName() == null || factory.getName().trim().isEmpty()) {
            throw new ValidationException("Factory name is required");
        }
        if (factory.getFactoryCode() == null || factory.getFactoryCode().trim().isEmpty()) {
            throw new ValidationException("Factory code is required");
        }
        if (factory.getFactoryCode().length() != 2) {
            throw new ValidationException("Factory code must be exactly 2 characters");
        }

        // Check for duplicates
        if (factoryRepository.existsByNameAndIsDeletedFalse(factory.getName())) {
            throw new DuplicateEntityException("Factory name already exists");
        }
        if (factoryRepository.existsByFactoryCodeAndIsDeletedFalse(factory.getFactoryCode())) {
            throw new DuplicateEntityException("Factory code already exists");
        }
    }

    private void validateFactoryForUpdate(Factory factoryDetails, Factory existingFactory) {
        // Validate required fields
        if (factoryDetails.getName() == null || factoryDetails.getName().trim().isEmpty()) {
            throw new ValidationException("Factory name is required");
        }
        if (factoryDetails.getFactoryCode() == null || factoryDetails.getFactoryCode().trim().isEmpty()) {
            throw new ValidationException("Factory code is required");
        }

        // Validate factory code length
        if (factoryDetails.getFactoryCode().length() != 2) {
            throw new ValidationException("Factory code must be exactly 2 characters");
        }

        // Check for duplicates only if values are changing
        if (!factoryDetails.getName().equals(existingFactory.getName())) {
            if (factoryRepository.existsByNameAndIsDeletedFalse(factoryDetails.getName())) {
                throw new DuplicateEntityException("Factory name already exists");
            }
        }
        if (!factoryDetails.getFactoryCode().equals(existingFactory.getFactoryCode())) {
            if (factoryRepository.existsByFactoryCodeAndIsDeletedFalse(factoryDetails.getFactoryCode())) {
                throw new DuplicateEntityException("Factory code already exists");
            }
        }
    }
}