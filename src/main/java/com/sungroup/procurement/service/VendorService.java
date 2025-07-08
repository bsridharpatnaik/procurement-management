package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.dto.response.VendorNameDto;
import com.sungroup.procurement.entity.Vendor;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.MaterialPriceHistoryRepository;
import com.sungroup.procurement.repository.ProcurementLineItemRepository;
import com.sungroup.procurement.repository.VendorRepository;
import com.sungroup.procurement.specification.VendorSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final FilterService filterService;

    private final ProcurementLineItemRepository procurementLineItemRepository;
    private final MaterialPriceHistoryRepository priceHistoryRepository;

    // READ Operations
    public ApiResponse<List<Vendor>> findVendorsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<Vendor> spec = buildVendorSpecification(filterData);
            Page<Vendor> vendorPage = vendorRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(vendorPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendorPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching vendors with filters", e);
            return ApiResponse.error("Failed to fetch vendors: " + e.getMessage());
        }
    }

    public ApiResponse<Vendor> findById(Long id) {
        try {
            Vendor vendor = vendorRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendor);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching vendor by id: {}", id, e);
            return ApiResponse.error("Failed to fetch vendor");
        }
    }

    public ApiResponse<List<Vendor>> searchVendors(String keyword, Pageable pageable) {
        try {
            Specification<Vendor> spec = VendorSpecification.isNotDeleted()
                    .and(VendorSpecification.searchByKeyword(keyword));

            Page<Vendor> vendorPage = vendorRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(vendorPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendorPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error searching vendors with keyword: {}", keyword, e);
            return ApiResponse.error("Failed to search vendors");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<Vendor> createVendor(Vendor vendor) {
        try {
            preprocessVendor(vendor);
            validateVendorForCreate(vendor);
            Vendor savedVendor = vendorRepository.save(vendor);
            log.info("Vendor created successfully: {}", savedVendor.getName());
            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedVendor);
        } catch (ValidationException | DuplicateEntityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating vendor", e);
            return ApiResponse.error("Failed to create vendor");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<Vendor> updateVendor(Long id, Vendor vendorDetails) {
        try {
            Vendor existingVendor = vendorRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            preprocessVendor(vendorDetails);
            validateVendorForUpdate(vendorDetails, existingVendor);
            // Update fields
            if (vendorDetails.getName() != null) {
                existingVendor.setName(vendorDetails.getName());
            }
            if (vendorDetails.getContactNumber() != null) {
                existingVendor.setContactNumber(vendorDetails.getContactNumber());
            }
            if (vendorDetails.getEmail() != null) {
                existingVendor.setEmail(vendorDetails.getEmail());
            }
            Vendor updatedVendor = vendorRepository.save(existingVendor);
            log.info("Vendor updated successfully: {}", updatedVendor.getName());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedVendor);
        } catch (EntityNotFoundException | ValidationException | DuplicateEntityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating vendor with id: {}", id, e);
            return ApiResponse.error("Failed to update vendor");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteVendor(Long id) {
        try {
            Vendor vendor = vendorRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            List<String> dependencies = new ArrayList<>();

            // Check procurement line items
            long lineItemCount = procurementLineItemRepository.countByAssignedVendorIdAndProcurementRequestIsDeletedFalse(id);
            if (lineItemCount > 0) {
                dependencies.add("Procurement Line Items: " + lineItemCount + " items");
            }

            // Check price history
            long priceHistoryCount = priceHistoryRepository.countByVendorId(id);
            if (priceHistoryCount > 0) {
                dependencies.add("Price History: " + priceHistoryCount + " records");
            }

            if (!dependencies.isEmpty()) {
                String message = "Cannot delete vendor '" + vendor.getName() +
                        "'. It is being used in: " + String.join("; ", dependencies);
                return ApiResponse.error(message);
            }

            // Soft delete if no dependencies
            vendor.setIsDeleted(true);
            vendorRepository.save(vendor);

            log.info("Vendor soft deleted successfully: {}", vendor.getName());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS,
                    "Vendor '" + vendor.getName() + "' deleted successfully");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting vendor with id: {}", id, e);
            return ApiResponse.error("Failed to delete vendor");
        }
    }

    // UTILITY Methods
    private Specification<Vendor> buildVendorSpecification(FilterDataList filterData) {
        Specification<Vendor> spec = VendorSpecification.isNotDeleted();

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        // CHANGE: Get multiple names from filter (instead of single name)
        List<String> names = filterService.getStringValues(filterData, "name");
        String contactNumber = filterService.getStringValue(filterData, "contactNumber");
        String email = filterService.getStringValue(filterData, "email");
        List<Long> ids = filterService.getLongValues(filterData, "ids");
        String createdBy = filterService.getStringValue(filterData, "createdBy");
        String keyword = filterService.getStringValue(filterData, "keyword");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        // CHANGE: Enhanced name filtering - supports multiple names
        if (names != null && !names.isEmpty()) {
            if (names.size() == 1) {
                // Single name - use contains (partial match)
                spec = spec.and(VendorSpecification.hasName(names.get(0)));
            } else {
                // Multiple names - use OR logic with partial match
                spec = spec.and(VendorSpecification.searchByMultipleNames(names));
            }
        }

        if (contactNumber != null) spec = spec.and(VendorSpecification.hasContactNumber(contactNumber));
        if (email != null) spec = spec.and(VendorSpecification.hasEmail(email));
        if (ids != null && !ids.isEmpty()) spec = spec.and(VendorSpecification.hasIds(ids));
        if (createdBy != null) spec = spec.and(VendorSpecification.createdBy(createdBy));
        if (keyword != null) spec = spec.and(VendorSpecification.searchByKeyword(keyword));

        if (createdDateRange != null) {
            spec = spec.and(VendorSpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        return spec;
    }

    private void validateVendorForCreate(Vendor vendor) {
        if (vendor.getName() == null || vendor.getName().trim().isEmpty()) {
            throw new ValidationException("Vendor name is required");
        }

        // After preprocessing, name should already be trimmed, but double-check
        String trimmedName = vendor.getName().trim();
        if (trimmedName.isEmpty()) {
            throw new ValidationException("Vendor name cannot be empty or only spaces");
        }

        // ADD: Case-insensitive duplicate check
        if (vendorRepository.existsByNameIgnoreCaseAndIsDeletedFalse(trimmedName)) {
            throw new DuplicateEntityException("Vendor name already exists");
        }

        // Validate email format if provided
        if (vendor.getEmail() != null && !vendor.getEmail().trim().isEmpty()) {
            if (!isValidEmail(vendor.getEmail())) {
                throw new ValidationException("Invalid email format");
            }
        }
    }

    private void validateVendorForUpdate(Vendor vendorDetails, Vendor existingVendor) {
        // Validate name if provided (name is required field)
        if (vendorDetails.getName() != null) {
            String trimmedName = vendorDetails.getName().trim();

            if (trimmedName.isEmpty()) {
                throw new ValidationException("Vendor name cannot be empty");
            }

            // ADD: Case-insensitive comparison and duplicate check
            if (!trimmedName.equalsIgnoreCase(existingVendor.getName())) {
                if (vendorRepository.existsByNameIgnoreCaseAndIsDeletedFalse(trimmedName)) {
                    throw new DuplicateEntityException("Vendor name already exists");
                }
            }
        }

        // Validate email format if provided
        if (vendorDetails.getEmail() != null) {
            if (!vendorDetails.getEmail().trim().isEmpty()) {
                if (!isValidEmail(vendorDetails.getEmail())) {
                    throw new ValidationException("Invalid email format");
                }
            }
        }

        // Validate contact number if provided
        if (vendorDetails.getContactNumber() != null && vendorDetails.getContactNumber().trim().isEmpty()) {
            throw new ValidationException("Contact number cannot be empty");
        }
    }

    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private void preprocessVendor(Vendor vendor) {
        // Trim name
        if (vendor.getName() != null) {
            vendor.setName(vendor.getName().trim());
        }

        // Trim email
        if (vendor.getEmail() != null) {
            String trimmedEmail = vendor.getEmail().trim();
            vendor.setEmail(trimmedEmail.isEmpty() ? null : trimmedEmail);
        }

        // Trim contact number
        if (vendor.getContactNumber() != null) {
            String trimmedContact = vendor.getContactNumber().trim();
            vendor.setContactNumber(trimmedContact.isEmpty() ? null : trimmedContact);
        }
    }

    public ApiResponse<List<String>> getAllVendorNames(String search) {
        try {
            List<String> vendorNames;

            if (search != null && !search.trim().isEmpty()) {
                // Filtered search
                vendorNames = vendorRepository.findVendorNamesByNameContainingIgnoreCase(search.trim());
            } else {
                // All vendor names
                vendorNames = vendorRepository.findAllActiveVendorNames();
            }

            // Sort alphabetically
            vendorNames.sort(String.CASE_INSENSITIVE_ORDER);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendorNames);
        } catch (Exception e) {
            log.error("Error fetching vendor names for typeahead with search: {}", search, e);
            return ApiResponse.error("Failed to fetch vendor names");
        }
    }

    public ApiResponse<List<VendorNameDto>> getVendorNamesWithIds(String search, Integer limit) {
        try {
            List<VendorNameDto> vendors;

            Pageable pageable = PageRequest.of(0, limit != null ? limit : 50, Sort.by("name"));

            if (search != null && !search.trim().isEmpty()) {
                // Filtered search with limit
                vendors = vendorRepository.findVendorNamesWithIdsByNameContaining(search.trim(), pageable);
            } else {
                // All vendors with limit
                vendors = vendorRepository.findAllActiveVendorNamesWithIds(pageable);
            }

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendors);
        } catch (Exception e) {
            log.error("Error fetching vendor names with IDs for typeahead", e);
            return ApiResponse.error("Failed to fetch vendor names with IDs");
        }
    }
}