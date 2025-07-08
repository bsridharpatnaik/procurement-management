package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.*;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.entity.enums.Priority;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.*;
import com.sungroup.procurement.specification.ProcurementRequestSpecification;
import com.sungroup.procurement.util.PermissionValidator;
import com.sungroup.procurement.util.ResponseFilterUtil;
import com.sungroup.procurement.util.SecurityUtil;
import com.sungroup.procurement.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementRequestService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final VendorRepository vendorRepository;
    private final ProcurementLineItemRepository lineItemRepository;
    private final MaterialPriceHistoryRepository priceHistoryRepository;
    private final FilterService filterService;
    private final ReturnRequestService returnRequestService;

    // READ Operations with Enhanced Access Control and Filtering
    public ApiResponse<List<ProcurementRequest>> findRequestsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            // CRITICAL: Always start with security and not deleted specification
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();

            // Add additional filters
            spec = spec.and(buildAdditionalFilters(filterData));

            Page<ProcurementRequest> requestPage = procurementRequestRepository.findAll(spec, pageable);

            // Apply vendor information filtering based on user role
            List<ProcurementRequest> filteredContent = ResponseFilterUtil.filterProcurementRequestsForUser(requestPage.getContent());

            PaginationResponse pagination = PaginationResponse.from(requestPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredContent, pagination);
        } catch (Exception e) {
            log.error("Error fetching procurement requests with filters", e);
            return ApiResponse.error("Failed to fetch procurement requests: " + e.getMessage());
        }
    }

    public ApiResponse<ProcurementRequest> findById(Long id) {
        try {
            // Use security specification for access control
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Apply vendor information filtering
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(request);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching procurement request by id: {}", id, e);
            return ApiResponse.error("Failed to fetch procurement request");
        }
    }

    public ApiResponse<ProcurementRequest> findByRequestNumber(String requestNumber) {
        try {
            // Use security specification for access control
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and(ProcurementRequestSpecification.hasRequestNumber(requestNumber));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Apply vendor information filtering
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(request);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching procurement request by number: {}", requestNumber, e);
            return ApiResponse.error("Failed to fetch procurement request");
        }
    }

    public ApiResponse<List<ProcurementRequest>> findUnassignedRequests() {
        try {
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and(ProcurementRequestSpecification.isUnassigned());

            List<ProcurementRequest> requests = procurementRequestRepository.findAll(spec);
            List<ProcurementRequest> filteredRequests = ResponseFilterUtil.filterProcurementRequestsForUser(requests);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequests);
        } catch (Exception e) {
            log.error("Error fetching unassigned requests", e);
            return ApiResponse.error("Failed to fetch unassigned requests");
        }
    }

    public ApiResponse<List<ProcurementRequest>> findRequestsRequiringApproval() {
        try {
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and(ProcurementRequestSpecification.requiresApproval(true));

            List<ProcurementRequest> requests = procurementRequestRepository.findAll(spec);
            List<ProcurementRequest> filteredRequests = ResponseFilterUtil.filterProcurementRequestsForUser(requests);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequests);
        } catch (Exception e) {
            log.error("Error fetching requests requiring approval", e);
            return ApiResponse.error("Failed to fetch requests requiring approval");
        }
    }

    // CREATE Operations with Enhanced Validation
    @Transactional
    public ApiResponse<ProcurementRequest> createProcurementRequest(ProcurementRequest request) {
        try {
            validateProcurementRequestForCreate(request);

            // Validate factory access for current user
            SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "create procurement request");

            // Generate request number
            String requestNumber = generateRequestNumber(request.getFactory());
            request.setRequestNumber(requestNumber);

            // Validate and set factory
            Factory factory = factoryRepository.findByIdActive(request.getFactory().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));
            request.setFactory(factory);

            // Validate line items
            validateLineItems(request.getLineItems());

            // Validate for duplicates (both within request and across requests)
            validateNoDuplicates(request);

            ProcurementRequest savedRequest = procurementRequestRepository.save(request);
            log.info("Procurement request created successfully: {}", savedRequest.getRequestNumber());

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(savedRequest);

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating procurement request", e);
            return ApiResponse.error("Failed to create procurement request");
        }
    }

    // UPDATE Operations with Enhanced Permission Validation
    @Transactional
    public ApiResponse<ProcurementRequest> updateProcurementRequest(Long id, ProcurementRequest requestDetails) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest existingRequest = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Enhanced permission validation
            PermissionValidator.validateCanEditProcurementRequest(existingRequest);

            // Update allowed fields based on status and user role
            updateAllowedFieldsWithValidation(existingRequest, requestDetails);

            ProcurementRequest updatedRequest = procurementRequestRepository.save(existingRequest);
            log.info("Procurement request updated successfully: {}", updatedRequest.getRequestNumber());

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(updatedRequest);

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to update procurement request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> updateStatus(Long id, ProcurementStatus newStatus) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Enhanced status change validation
            PermissionValidator.validateStatusTransition(request, newStatus);

            // Special validation for closing requests with returns
            if (newStatus == ProcurementStatus.CLOSED) {
                if (!returnRequestService.canCloseProcurementRequest(id)) {
                    throw new ValidationException("Cannot close request with pending return requests");
                }
            }

            request.setStatus(newStatus);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request status updated: {} -> {}", request.getRequestNumber(), newStatus);

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(updatedRequest);

            return ApiResponse.success("Status updated successfully", filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating status for request id: {}", id, e);
            return ApiResponse.error("Failed to update status");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> assignToUser(Long id, Long userId) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            User user = userRepository.findByIdAndIsDeletedFalse(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // Enhanced assignment validation
            PermissionValidator.validateAssignmentPermission(user);

            request.setAssignedTo(user);
            if (request.getStatus() == ProcurementStatus.SUBMITTED) {
                request.setStatus(ProcurementStatus.IN_PROGRESS);
            }

            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request assigned: {} -> {}", request.getRequestNumber(), user.getUsername());

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(updatedRequest);

            return ApiResponse.success("Request assigned successfully", filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning request id: {} to user id: {}", id, userId, e);
            return ApiResponse.error("Failed to assign request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> setApprovalFlag(Long id, boolean requiresApproval) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Enhanced approval flag validation
            PermissionValidator.validateApprovalFlagPermission(request, requiresApproval);

            request.setRequiresApproval(requiresApproval);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Approval flag set for request: {} to: {}", request.getRequestNumber(), requiresApproval);

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(updatedRequest);

            String message = requiresApproval ? "Request marked for approval" : "Approval requirement removed";
            return ApiResponse.success(message, filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error setting approval flag for request id: {}", id, e);
            return ApiResponse.error("Failed to set approval flag");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> approveRequest(Long id, Long approverId) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Enhanced approval validation
            PermissionValidator.validateApprovalPermission(request);

            User approver = userRepository.findByIdAndIsDeletedFalse(approverId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            request.setApprovedBy(approver);
            request.setApprovedDate(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
            request.setRequiresApproval(false);

            ProcurementRequest approvedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request approved: {} by {} at {}",
                    request.getRequestNumber(),
                    approver.getUsername(),
                    TimeUtil.formatIST(request.getApprovedDate()));

            // Apply vendor information filtering before returning
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(approvedRequest);

            return ApiResponse.success("Request approved successfully", filteredRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error approving request id: {}", id, e);
            return ApiResponse.error("Failed to approve request");
        }
    }

    // DELETE Operations with Enhanced Validation
    @Transactional
    public ApiResponse<String> deleteProcurementRequestRestricted(Long id) {
        try {
            // Find with security filter
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, cb) -> cb.equal(root.get("id"), id));

            ProcurementRequest request = procurementRequestRepository.findOne(spec)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Enhanced deletion validation
            PermissionValidator.validateDeletionPermission(request);

            // Check if any line items have been processed (have vendor assignments, prices, etc.)
            boolean hasProcessedLineItems = request.getLineItems().stream()
                    .anyMatch(item -> item.getAssignedVendor() != null ||
                            item.getAssignedPrice() != null ||
                            item.getStatus() != LineItemStatus.PENDING);

            if (hasProcessedLineItems) {
                return ApiResponse.error("Cannot delete request. Some line items have been processed.");
            }

            // Soft delete
            request.setIsDeleted(true);
            procurementRequestRepository.save(request);

            log.info("Procurement request soft deleted: {}", request.getRequestNumber());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS, "Procurement request deleted successfully");
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to delete procurement request");
        }
    }

    // UTILITY Methods with Enhanced Validation
    private void updateAllowedFieldsWithValidation(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        // Validate each field that's being updated
        if (requestDetails.getJustification() != null) {
            PermissionValidator.validateFieldEditPermission(existingRequest, "justification", requestDetails.getJustification());
            existingRequest.setJustification(requestDetails.getJustification());
        }

        if (requestDetails.getPriority() != null) {
            PermissionValidator.validateFieldEditPermission(existingRequest, "priority", requestDetails.getPriority());
            existingRequest.setPriority(requestDetails.getPriority());
        }

        if (requestDetails.getExpectedDeliveryDate() != null) {
            PermissionValidator.validateFieldEditPermission(existingRequest, "expectedDeliveryDate", requestDetails.getExpectedDeliveryDate());
            existingRequest.setExpectedDeliveryDate(requestDetails.getExpectedDeliveryDate());
        }

        if (requestDetails.getAssignedTo() != null) {
            PermissionValidator.validateFieldEditPermission(existingRequest, "assignedTo", requestDetails.getAssignedTo());
            User assignedUser = userRepository.findByIdAndIsDeletedFalse(requestDetails.getAssignedTo().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));
            PermissionValidator.validateAssignmentPermission(assignedUser);
            existingRequest.setAssignedTo(assignedUser);
        }

        if (requestDetails.getRequiresApproval() != null) {
            PermissionValidator.validateFieldEditPermission(existingRequest, "requiresApproval", requestDetails.getRequiresApproval());
            PermissionValidator.validateApprovalFlagPermission(existingRequest, requestDetails.getRequiresApproval());
            existingRequest.setRequiresApproval(requestDetails.getRequiresApproval());
        }
    }

    private Specification<ProcurementRequest> buildAdditionalFilters(FilterDataList filterData) {
        Specification<ProcurementRequest> spec = Specification.where(null);

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        String requestNumber = filterService.getStringValue(filterData, "requestNumber");
        List<ProcurementStatus> statuses = filterService.getEnumValues(filterData, "status", ProcurementStatus.class);
        List<Priority> priorities = filterService.getEnumValues(filterData, "priority", Priority.class);
        List<Long> factoryIds = filterService.getLongValues(filterData, "factoryId");
        String factoryName = filterService.getStringValue(filterData, "factoryName");
        List<Long> assignedToIds = filterService.getLongValues(filterData, "assignedTo");
        Boolean requiresApproval = filterService.getBooleanValue(filterData, "requiresApproval");
        List<Long> approvedByIds = filterService.getLongValues(filterData, "approvedBy");
        Boolean isShortClosed = filterService.getBooleanValue(filterData, "isShortClosed");
        String createdBy = filterService.getStringValue(filterData, "createdBy");
        Long materialId = filterService.getLongValue(filterData, "materialId");
        String materialName = filterService.getStringValue(filterData, "materialName");
        Long vendorId = filterService.getLongValue(filterData, "vendorId");
        Integer pendingDays = filterService.getIntegerValue(filterData, "pendingDays");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");
        FilterService.DateRange expectedDeliveryRange = filterService.getDateRange(filterData, "expectedStartDate", "expectedEndDate");

        if (requestNumber != null) spec = spec.and(ProcurementRequestSpecification.hasRequestNumber(requestNumber));
        if (statuses != null && !statuses.isEmpty()) spec = spec.and(ProcurementRequestSpecification.hasStatuses(statuses));
        if (priorities != null && !priorities.isEmpty()) spec = spec.and(ProcurementRequestSpecification.hasPriorities(priorities));

        // Factory filtering: Validate access for explicit factory filters
        if (factoryIds != null && !factoryIds.isEmpty()) {
            // Validate user has access to requested factories
            for (Long factoryId : factoryIds) {
                if (!SecurityUtil.hasAccessToFactory(factoryId)) {
                    log.warn("User {} attempted to filter by inaccessible factory ID: {}",
                            SecurityUtil.getCurrentUsername(), factoryId);
                    continue;
                }
            }
            spec = spec.and(ProcurementRequestSpecification.hasFactoryIds(factoryIds));
        }

        if (factoryName != null) spec = spec.and(ProcurementRequestSpecification.hasFactoryName(factoryName));
        if (assignedToIds != null && !assignedToIds.isEmpty()) spec = spec.and(ProcurementRequestSpecification.isAssignedToUsers(assignedToIds));
        if (requiresApproval != null) spec = spec.and(ProcurementRequestSpecification.requiresApproval(requiresApproval));
        if (approvedByIds != null && !approvedByIds.isEmpty()) spec = spec.and(ProcurementRequestSpecification.isApprovedBy(approvedByIds.get(0)));
        if (isShortClosed != null) spec = spec.and(ProcurementRequestSpecification.isShortClosed(isShortClosed));
        if (createdBy != null) spec = spec.and(ProcurementRequestSpecification.createdBy(createdBy));
        if (materialId != null) spec = spec.and(ProcurementRequestSpecification.hasMaterialId(materialId));
        if (materialName != null) spec = spec.and(ProcurementRequestSpecification.hasMaterialName(materialName));

        // Vendor filtering: Only allow for users who can see vendor information
        if (vendorId != null && SecurityUtil.canSeeVendorInformation()) {
            spec = spec.and(ProcurementRequestSpecification.hasVendorId(vendorId));
        }

        if (pendingDays != null) spec = spec.and(ProcurementRequestSpecification.pendingForDays(pendingDays));

        if (createdDateRange != null) {
            spec = spec.and(ProcurementRequestSpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        if (expectedDeliveryRange != null) {
            LocalDate startDate = expectedDeliveryRange.getStartDate() != null ?
                    expectedDeliveryRange.getStartDate().toLocalDate() : null;
            LocalDate endDate = expectedDeliveryRange.getEndDate() != null ?
                    expectedDeliveryRange.getEndDate().toLocalDate() : null;
            spec = spec.and(ProcurementRequestSpecification.expectedDeliveryBetween(startDate, endDate));
        }

        return spec;
    }

    /**
     * Enhanced duplicate validation - both within request and across requests
     */
    private void validateNoDuplicates(ProcurementRequest request) {
        // 1. Check for duplicate materials within the same request
        long distinctMaterials = request.getLineItems().stream()
                .map(item -> item.getMaterial().getId())
                .distinct()
                .count();

        if (distinctMaterials != request.getLineItems().size()) {
            throw new ValidationException("Duplicate materials found in the same request");
        }

        // 2. Check for duplicate requests from same factory in last 5 minutes
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        Specification<ProcurementRequest> duplicateSpec = ProcurementRequestSpecification.isNotDeleted()
                .and((root, query, cb) -> cb.equal(root.get("factory").get("id"), request.getFactory().getId()))
                .and((root, query, cb) -> cb.greaterThan(root.get("createdAt"), fiveMinutesAgo));

        List<ProcurementRequest> recentRequests = procurementRequestRepository.findAll(duplicateSpec);

        for (ProcurementRequest recentRequest : recentRequests) {
            if (hasSameMaterialsAndQuantities(request, recentRequest)) {
                throw new ValidationException(
                        "A similar request with the same materials and quantities was created recently. " +
                                "Please check request: " + recentRequest.getRequestNumber()
                );
            }
        }
    }

    /**
     * Check if two requests have same materials and quantities (Java 8 compatible)
     */
    private boolean hasSameMaterialsAndQuantities(ProcurementRequest request1, ProcurementRequest request2) {
        if (request1.getLineItems().size() != request2.getLineItems().size()) {
            return false;
        }

        // Create maps for comparison
        Map<Long, BigDecimal> request1Items = request1.getLineItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMaterial().getId(),
                        item -> item.getRequestedQuantity()
                ));

        Map<Long, BigDecimal> request2Items = request2.getLineItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMaterial().getId(),
                        item -> item.getRequestedQuantity()
                ));

        return request1Items.equals(request2Items);
    }

    private String generateRequestNumber(Factory factory) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String factoryCode = factory.getFactoryCode();

        String prefix = ProjectConstants.REQUEST_NUMBER_PREFIX + "-" + factoryCode + "-" + year + "-";

        long count = procurementRequestRepository.count() + 1;
        String sequence = String.format("%03d", count);

        return prefix + sequence;
    }

    private void validateProcurementRequestForCreate(ProcurementRequest request) {
        if (request.getFactory() == null || request.getFactory().getId() == null) {
            throw new ValidationException("Factory is required");
        }
        if (request.getPriority() == null) {
            request.setPriority(Priority.MEDIUM);
        }
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            throw new ValidationException("At least one line item is required");
        }
    }

    private void validateLineItems(List<ProcurementLineItem> lineItems) {
        for (ProcurementLineItem lineItem : lineItems) {
            if (lineItem.getMaterial() == null || lineItem.getMaterial().getId() == null) {
                throw new ValidationException("Material is required for all line items");
            }
            if (lineItem.getRequestedQuantity() == null || lineItem.getRequestedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Requested quantity must be greater than zero");
            }

            materialRepository.findByIdActive(lineItem.getMaterial().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Material not found with id: " + lineItem.getMaterial().getId()));
        }
    }
}