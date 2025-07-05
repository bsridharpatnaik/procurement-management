package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Vendor;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.VendorRepository;
import com.sungroup.procurement.specification.VendorSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final FilterService filterService;

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
            Vendor vendor = vendorRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendor);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching vendor by id: {}", id, e);
            return ApiResponse.error("Failed to fetch vendor");
        }
    }

    public ApiResponse<Vendor> findByEmail(String email) {
        try {
            Vendor vendor = vendorRepository.findByEmailAndIsDeletedFalse(email)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendor);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching vendor by email: {}", email, e);
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

    public ApiResponse<List<Vendor>> findVendorsWithValidEmail() {
        try {
            Specification<Vendor> spec = VendorSpecification.isNotDeleted()
                    .and(VendorSpecification.hasValidEmail());

            List<Vendor> vendors = vendorRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendors);
        } catch (Exception e) {
            log.error("Error fetching vendors with valid email", e);
            return ApiResponse.error("Failed to fetch vendors");
        }
    }

    public ApiResponse<List<Vendor>> findVendorsWithValidContact() {
        try {
            Specification<Vendor> spec = VendorSpecification.isNotDeleted()
                    .and(VendorSpecification.hasValidContactNumber());

            List<Vendor> vendors = vendorRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, vendors);
        } catch (Exception e) {
            log.error("Error fetching vendors with valid contact", e);
            return ApiResponse.error("Failed to fetch vendors");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<Vendor> createVendor(Vendor vendor) {
        try {
            validateVendorForCreate(vendor);

            Vendor savedVendor = vendorRepository.save(vendor);
            log.info("Vendor created successfully: {}", savedVendor.getName());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedVendor);
        } catch (ValidationException e) {
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

            validateVendorForUpdate(vendorDetails);

            // Update fields
            existingVendor.setName(vendorDetails.getName());
            existingVendor.setContactNumber(vendorDetails.getContactNumber());
            existingVendor.setEmail(vendorDetails.getEmail());

            Vendor updatedVendor = vendorRepository.save(existingVendor);
            log.info("Vendor updated successfully: {}", updatedVendor.getName());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedVendor);
        } catch (EntityNotFoundException | ValidationException e) {
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

            // Soft delete
            vendor.setIsDeleted(true);
            vendorRepository.save(vendor);

            log.info("Vendor soft deleted successfully: {}", vendor.getName());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS, "Vendor deleted successfully");
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

        String name = filterService.getStringValue(filterData, "name");
        String contactNumber = filterService.getStringValue(filterData, "contactNumber");
        String email = filterService.getStringValue(filterData, "email");
        List<Long> ids = filterService.getLongValues(filterData, "ids");
        String createdBy = filterService.getStringValue(filterData, "createdBy");
        String keyword = filterService.getStringValue(filterData, "keyword");
        Boolean hasValidEmail = filterService.getBooleanValue(filterData, "hasValidEmail");
        Boolean hasValidContact = filterService.getBooleanValue(filterData, "hasValidContact");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        if (name != null) spec = spec.and(VendorSpecification.hasName(name));
        if (contactNumber != null) spec = spec.and(VendorSpecification.hasContactNumber(contactNumber));
        if (email != null) spec = spec.and(VendorSpecification.hasEmail(email));
        if (ids != null && !ids.isEmpty()) spec = spec.and(VendorSpecification.hasIds(ids));
        if (createdBy != null) spec = spec.and(VendorSpecification.createdBy(createdBy));
        if (keyword != null) spec = spec.and(VendorSpecification.searchByKeyword(keyword));
        if (hasValidEmail != null && hasValidEmail) spec = spec.and(VendorSpecification.hasValidEmail());
        if (hasValidContact != null && hasValidContact) spec = spec.and(VendorSpecification.hasValidContactNumber());
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

        // Validate email format if provided
        if (vendor.getEmail() != null && !vendor.getEmail().trim().isEmpty()) {
            if (!isValidEmail(vendor.getEmail())) {
                throw new ValidationException("Invalid email format");
            }
        }
    }

    private void validateVendorForUpdate(Vendor vendorDetails) {
        // Validate email format if provided
        if (vendorDetails.getEmail() != null && !vendorDetails.getEmail().trim().isEmpty()) {
            if (!isValidEmail(vendorDetails.getEmail())) {
                throw new ValidationException("Invalid email format");
            }
        }
    }

    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }
}