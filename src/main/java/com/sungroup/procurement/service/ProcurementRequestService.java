package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.*;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.entity.enums.Priority;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.*;
import com.sungroup.procurement.specification.ProcurementRequestSpecification;
import com.sungroup.procurement.util.SecurityUtil;
import com.sungroup.procurement.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    private final ReturnRequestRepository returnRequestRepository;
    private final FilterService filterService;
    private final ReturnRequestService returnRequestService;

    // READ Operations with Factory Access Control
    public ApiResponse<List<ProcurementRequest>> findRequestsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<ProcurementRequest> spec = buildProcurementRequestSpecification(filterData);

            // Apply factory access control
            spec = applyFactoryAccessControl(spec);

            Page<ProcurementRequest> requestPage = procurementRequestRepository.findAll(spec, pageable);

            // Filter vendor information for factory users
            List<ProcurementRequest> filteredRequests = filterVendorInformationForFactoryUsers(requestPage.getContent());

            PaginationResponse pagination = PaginationResponse.from(requestPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequests, pagination);
        } catch (Exception e) {
            log.error("Error fetching procurement requests with filters", e);
            return ApiResponse.error("Failed to fetch procurement requests: " + e.getMessage());
        }
    }

    public ApiResponse<ProcurementRequest> findById(Long id) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            // Filter vendor information for factory users
            ProcurementRequest filteredRequest = filterVendorInformationForFactoryUser(request);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching procurement request by id: {}", id, e);
            return ApiResponse.error("Failed to fetch procurement request");
        }
    }

    public ApiResponse<ProcurementRequest> findByRequestNumber(String requestNumber) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByRequestNumberAndIsDeletedFalse(requestNumber)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            // Filter vendor information for factory users
            ProcurementRequest filteredRequest = filterVendorInformationForFactoryUser(request);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequest);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching procurement request by number: {}", requestNumber, e);
            return ApiResponse.error("Failed to fetch procurement request");
        }
    }

    public ApiResponse<List<ProcurementRequest>> findUnassignedRequests() {
        try {
            // Only purchase team and management can see unassigned requests
            if (SecurityUtil.isCurrentUserFactoryUser()) {
                return ApiResponse.error("Factory users cannot view unassigned requests");
            }

            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted()
                    .and(ProcurementRequestSpecification.isUnassigned());

            List<ProcurementRequest> requests = procurementRequestRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, requests);
        } catch (Exception e) {
            log.error("Error fetching unassigned requests", e);
            return ApiResponse.error("Failed to fetch unassigned requests");
        }
    }

    public ApiResponse<List<ProcurementRequest>> findRequestsRequiringApproval() {
        try {
            // Only management can see requests requiring approval
            if (!SecurityUtil.isCurrentUserManagement()) {
                return ApiResponse.error("Only management users can view requests requiring approval");
            }

            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted()
                    .and(ProcurementRequestSpecification.requiresApproval(true));

            List<ProcurementRequest> requests = procurementRequestRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, requests);
        } catch (Exception e) {
            log.error("Error fetching requests requiring approval", e);
            return ApiResponse.error("Failed to fetch requests requiring approval");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<ProcurementRequest> createProcurementRequest(ProcurementRequest request) {
        try {
            // Validate user permissions - only factory users can create requests
            if (!SecurityUtil.isCurrentUserFactoryUser()) {
                throw new SecurityException("Only factory users can create procurement requests");
            }

            validateProcurementRequestForCreate(request);

            // Validate factory access
            validateFactoryAccessForCreation(request.getFactory().getId());

            // Generate request number
            String requestNumber = generateRequestNumber(request.getFactory());
            request.setRequestNumber(requestNumber);

            // Validate and set factory
            Factory factory = factoryRepository.findByIdActive(request.getFactory().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));
            request.setFactory(factory);

            // Validate line items
            validateLineItems(request.getLineItems());

            // Check for duplicate requests
            validateNoDuplicateRequest(request);

            // Set initial status
            request.setStatus(ProcurementStatus.DRAFT);

            ProcurementRequest savedRequest = procurementRequestRepository.save(request);
            log.info("Procurement request created successfully: {}", savedRequest.getRequestNumber());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating procurement request", e);
            return ApiResponse.error("Failed to create procurement request");
        }
    }

    // UPDATE Operations with Status-Based Edit Restrictions
    @Transactional
    public ApiResponse<ProcurementRequest> updateProcurementRequest(Long id, ProcurementRequest requestDetails) {
        try {
            ProcurementRequest existingRequest = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(existingRequest);

            // Validate edit permissions based on status and role
            validateEditPermissions(existingRequest, requestDetails);

            // Validate update rules
            validateProcurementRequestForUpdate(requestDetails, existingRequest);

            // Update only allowed fields based on status and role
            updateAllowedFields(existingRequest, requestDetails);

            ProcurementRequest updatedRequest = procurementRequestRepository.save(existingRequest);
            log.info("Procurement request updated successfully: {}", updatedRequest.getRequestNumber());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to update procurement request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> submitRequest(Long id) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate user can submit (factory user and creator)
            validateSubmissionPermissions(request);

            // Validate status
            if (request.getStatus() != ProcurementStatus.DRAFT) {
                throw new ValidationException("Only draft requests can be submitted");
            }

            // Validate request has line items
            if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
                throw new ValidationException("Cannot submit request without line items");
            }

            request.setStatus(ProcurementStatus.SUBMITTED);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request submitted: {}", request.getRequestNumber());
            return ApiResponse.success("Request submitted successfully", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error submitting request id: {}", id, e);
            return ApiResponse.error("Failed to submit request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> updateStatus(Long id, ProcurementStatus newStatus) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            // Validate status change permissions
            validateStatusChangePermissions(request, newStatus);

            validateStatusTransition(request.getStatus(), newStatus);

            request.setStatus(newStatus);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request status updated: {} -> {}", request.getRequestNumber(), newStatus);
            return ApiResponse.success("Status updated successfully", updatedRequest);
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
            // Only purchase team can assign requests
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can assign requests");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate status
            if (request.getStatus() != ProcurementStatus.SUBMITTED) {
                throw new ValidationException("Only submitted requests can be assigned");
            }

            // Validate user exists and has correct role
            User user = userRepository.findByIdAndIsDeletedFalse(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            if (user.getRole() != UserRole.PURCHASE_TEAM && user.getRole() != UserRole.MANAGEMENT) {
                throw new ValidationException("Can only assign to purchase team members");
            }

            request.setAssignedTo(user);
            request.setStatus(ProcurementStatus.IN_PROGRESS);

            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request assigned: {} -> {}", request.getRequestNumber(), user.getUsername());
            return ApiResponse.success("Request assigned successfully", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning request id: {} to user id: {}", id, userId, e);
            return ApiResponse.error("Failed to assign request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> markForApproval(Long id) {
        try {
            // Only purchase team can mark for approval
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can mark requests for approval");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate status
            if (request.getStatus() != ProcurementStatus.SUBMITTED &&
                    request.getStatus() != ProcurementStatus.IN_PROGRESS) {
                throw new ValidationException("Can only mark submitted or in-progress requests for approval");
            }

            request.setRequiresApproval(true);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request marked for approval: {}", request.getRequestNumber());
            return ApiResponse.success("Request marked for approval", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error marking request for approval: {}", id, e);
            return ApiResponse.error("Failed to mark request for approval");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> approveRequest(Long id, Long approverId) {
        try {
            // Only management can approve
            if (!SecurityUtil.isCurrentUserManagement()) {
                throw new SecurityException("Only management can approve requests");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate approval required
            if (!request.getRequiresApproval()) {
                throw new ValidationException("Request does not require approval");
            }

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

            return ApiResponse.success("Request approved successfully", approvedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error approving request id: {}", id, e);
            return ApiResponse.error("Failed to approve request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> receiveRequest(Long id, Map<Long, BigDecimal> actualQuantities) {
        try {
            // Only factory users can mark as received
            if (!SecurityUtil.isCurrentUserFactoryUser()) {
                throw new SecurityException("Only factory users can mark requests as received");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            // Validate status
            if (request.getStatus() != ProcurementStatus.DISPATCHED) {
                throw new ValidationException("Only dispatched requests can be marked as received");
            }

            // Update line item actual quantities and status
            for (ProcurementLineItem lineItem : request.getLineItems()) {
                BigDecimal actualQuantity = actualQuantities.get(lineItem.getId());
                if (actualQuantity != null) {
                    lineItem.setActualQuantity(actualQuantity);
                    lineItem.setStatus(LineItemStatus.RECEIVED);
                }
            }

            request.setStatus(ProcurementStatus.RECEIVED);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request marked as received: {}", request.getRequestNumber());
            return ApiResponse.success("Request marked as received", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error marking request as received: {}", id, e);
            return ApiResponse.error("Failed to mark request as received");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> closeRequest(Long id) {
        try {
            // Only purchase team can close requests
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can close requests");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate status
            if (request.getStatus() != ProcurementStatus.RECEIVED) {
                throw new ValidationException("Only received requests can be closed");
            }

            // Check for pending returns
            if (!returnRequestService.canCloseProcurementRequest(id)) {
                throw new ValidationException("Cannot close request with pending return requests");
            }

            request.setStatus(ProcurementStatus.CLOSED);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request closed: {}", request.getRequestNumber());
            return ApiResponse.success("Request closed successfully", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error closing request: {}", id, e);
            return ApiResponse.error("Failed to close request");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteProcurementRequestRestricted(Long id) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate user can delete (factory user and creator)
            validateDeletionPermissions(request);

            // Only allow deletion of DRAFT requests that have no line items or only draft line items
            if (request.getStatus() != ProcurementStatus.DRAFT) {
                return ApiResponse.error("Only draft procurement requests can be deleted.");
            }

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
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to delete procurement request");
        }
    }

    // UTILITY Methods
    private Specification<ProcurementRequest> buildProcurementRequestSpecification(FilterDataList filterData) {
        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted();

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
        if (factoryIds != null && !factoryIds.isEmpty()) spec = spec.and(ProcurementRequestSpecification.hasFactoryIds(factoryIds));
        if (factoryName != null) spec = spec.and(ProcurementRequestSpecification.hasFactoryName(factoryName));
        if (assignedToIds != null && !assignedToIds.isEmpty()) spec = spec.and(ProcurementRequestSpecification.isAssignedToUsers(assignedToIds));
        if (requiresApproval != null) spec = spec.and(ProcurementRequestSpecification.requiresApproval(requiresApproval));
        if (approvedByIds != null && !approvedByIds.isEmpty()) spec = spec.and(ProcurementRequestSpecification.isApprovedBy(approvedByIds.get(0)));
        if (isShortClosed != null) spec = spec.and(ProcurementRequestSpecification.isShortClosed(isShortClosed));
        if (createdBy != null) spec = spec.and(ProcurementRequestSpecification.createdBy(createdBy));
        if (materialId != null) spec = spec.and(ProcurementRequestSpecification.hasMaterialId(materialId));
        if (materialName != null) spec = spec.and(ProcurementRequestSpecification.hasMaterialName(materialName));
        if (vendorId != null) spec = spec.and(ProcurementRequestSpecification.hasVendorId(vendorId));
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

    private Specification<ProcurementRequest> applyFactoryAccessControl(Specification<ProcurementRequest> spec) {
        if (SecurityUtil.isCurrentUserFactoryUser()) {
            List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
            if (accessibleFactoryIds.isEmpty()) {
                // Return specification that returns no results
                return (root, query, cb) -> cb.disjunction();
            }
            spec = spec.and(ProcurementRequestSpecification.accessibleByFactories(accessibleFactoryIds));
        }
        return spec;
    }

    private void validateFactoryAccess(ProcurementRequest request) {
        if (SecurityUtil.isCurrentUserFactoryUser()) {
            Long factoryId = request.getFactory().getId();
            List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
            if (!accessibleFactoryIds.contains(factoryId)) {
                throw new SecurityException("Access denied to this factory's procurement requests");
            }
        }
    }

    private void validateFactoryAccessForCreation(Long factoryId) {
        if (SecurityUtil.isCurrentUserFactoryUser()) {
            List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
            if (!accessibleFactoryIds.contains(factoryId)) {
                throw new SecurityException("Cannot create requests for this factory");
            }
        }
    }

    private List<ProcurementRequest> filterVendorInformationForFactoryUsers(List<ProcurementRequest> requests) {
        if (SecurityUtil.isCurrentUserFactoryUser()) {
            return requests.stream()
                    .map(this::filterVendorInformationForFactoryUser)
                    .collect(Collectors.toList());
        }
        return requests;
    }

    private ProcurementRequest filterVendorInformationForFactoryUser(ProcurementRequest request) {
        if (SecurityUtil.isCurrentUserFactoryUser()) {
            // Remove vendor information from line items
            if (request.getLineItems() != null) {
                request.getLineItems().forEach(lineItem -> {
                    lineItem.setAssignedVendor(null);
                    lineItem.setAssignedPrice(null);
                });
            }
        }
        return request;
    }

    private void validateEditPermissions(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        ProcurementStatus currentStatus = existingRequest.getStatus();

        // Check if approval is required
        if (existingRequest.getRequiresApproval() && currentUserRole != UserRole.MANAGEMENT) {
            throw new ValidationException("Request requires management approval before any changes can be made");
        }

        switch (currentStatus) {
            case DRAFT:
                // Only factory user (creator) can edit
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can edit draft requests");
                }
                validateIsCreator(existingRequest);
                break;

            case SUBMITTED:
                // Only purchase team can edit (assignment only)
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can edit submitted requests");
                }
                validateOnlyAssignmentChanges(requestDetails);
                break;

            case IN_PROGRESS:
                // Only purchase team can edit
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can edit in-progress requests");
                }
                break;

            case ORDERED:
            case DISPATCHED:
                // Only status changes allowed
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can update ordered/dispatched requests");
                }
                validateOnlyStatusChanges(requestDetails);
                break;

            case RECEIVED:
                // Only factory users can confirm receipt
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can update received requests");
                }
                validateIsFromSameFactory(existingRequest);
                break;

            case CLOSED:
                throw new ValidationException("Closed requests cannot be modified");
        }
    }

    private void validateIsCreator(ProcurementRequest request) {
        String currentUsername = SecurityUtil.getCurrentUsername();
        if (!currentUsername.equals(request.getCreatedBy())) {
            throw new SecurityException("Only the creator can edit this request");
        }
    }

    private void validateIsFromSameFactory(ProcurementRequest request) {
        Long factoryId = request.getFactory().getId();
        List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
        if (!accessibleFactoryIds.contains(factoryId)) {
            throw new SecurityException("Cannot update requests from other factories");
        }
    }

    private void validateOnlyAssignmentChanges(ProcurementRequest requestDetails) {
        // For SUBMITTED status, only assignedTo can be changed
        // This validation ensures no other fields are being modified
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                requestDetails.getLineItems() != null) {
            throw new ValidationException("Only assignment can be changed for submitted requests");
        }
    }

    private void validateOnlyStatusChanges(ProcurementRequest requestDetails) {
        // For ORDERED/DISPATCHED status, only status can be changed
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                requestDetails.getAssignedTo() != null ||
                requestDetails.getLineItems() != null) {
            throw new ValidationException("Only status can be changed for ordered/dispatched requests");
        }
    }

    private void validateSubmissionPermissions(ProcurementRequest request) {
        // Only factory user who created the request can submit
        if (!SecurityUtil.isCurrentUserFactoryUser()) {
            throw new SecurityException("Only factory users can submit requests");
        }

        validateIsCreator(request);
        validateFactoryAccess(request);
    }

    private void validateDeletionPermissions(ProcurementRequest request) {
        // Only factory user who created the request can delete
        if (!SecurityUtil.isCurrentUserFactoryUser()) {
            throw new SecurityException("Only factory users can delete requests");
        }

        validateIsCreator(request);
        validateFactoryAccess(request);
    }

    private void validateStatusChangePermissions(ProcurementRequest request, ProcurementStatus newStatus) {
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        ProcurementStatus currentStatus = request.getStatus();

        switch (newStatus) {
            case SUBMITTED:
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can submit requests");
                }
                validateIsCreator(request);
                break;

            case IN_PROGRESS:
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can move requests to in-progress");
                }
                break;

            case ORDERED:
            case DISPATCHED:
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can mark requests as ordered/dispatched");
                }
                break;

            case RECEIVED:
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can mark requests as received");
                }
                validateIsFromSameFactory(request);
                break;

            case CLOSED:
                if (currentUserRole != UserRole.PURCHASE_TEAM && currentUserRole != UserRole.MANAGEMENT) {
                    throw new SecurityException("Only purchase team can close requests");
                }
                break;
        }
    }

    private void validateStatusTransition(ProcurementStatus currentStatus, ProcurementStatus newStatus) {
        boolean isValidTransition = false;

        switch (currentStatus) {
            case DRAFT:
                isValidTransition = newStatus == ProcurementStatus.SUBMITTED;
                break;
            case SUBMITTED:
                isValidTransition = newStatus == ProcurementStatus.IN_PROGRESS;
                break;
            case IN_PROGRESS:
                isValidTransition = newStatus == ProcurementStatus.ORDERED;
                break;
            case ORDERED:
                isValidTransition = newStatus == ProcurementStatus.DISPATCHED;
                break;
            case DISPATCHED:
                isValidTransition = newStatus == ProcurementStatus.RECEIVED;
                break;
            case RECEIVED:
                isValidTransition = newStatus == ProcurementStatus.CLOSED;
                break;
            case CLOSED:
                isValidTransition = false; // No transitions from closed
                break;
        }

        if (!isValidTransition) {
            throw new ValidationException("Invalid status transition from " + currentStatus + " to " + newStatus);
        }
    }

    private void updateAllowedFields(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        ProcurementStatus status = existingRequest.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();

        switch (status) {
            case DRAFT:
                // Factory user can edit most fields
                updateDraftFields(existingRequest, requestDetails);
                break;

            case SUBMITTED:
                // Only assignment can be changed
                updateSubmittedFields(existingRequest, requestDetails);
                break;

            case IN_PROGRESS:
                // Purchase team can edit assignment and approval flag
                updateInProgressFields(existingRequest, requestDetails);
                break;

            case ORDERED:
            case DISPATCHED:
                // No field updates allowed except status (handled separately)
                break;

            case RECEIVED:
                // Factory users can confirm receipt (handled separately)
                break;

            case CLOSED:
                // No updates allowed
                break;
        }
    }

    private void updateDraftFields(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        if (requestDetails.getJustification() != null) {
            existingRequest.setJustification(requestDetails.getJustification());
        }
        if (requestDetails.getPriority() != null) {
            existingRequest.setPriority(requestDetails.getPriority());
        }
        if (requestDetails.getExpectedDeliveryDate() != null) {
            existingRequest.setExpectedDeliveryDate(requestDetails.getExpectedDeliveryDate());
        }
        // Note: Line items should be handled separately if needed
    }

    private void updateSubmittedFields(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        if (requestDetails.getAssignedTo() != null) {
            User assignedUser = userRepository.findByIdAndIsDeletedFalse(requestDetails.getAssignedTo().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));
            existingRequest.setAssignedTo(assignedUser);
        }
    }

    private void updateInProgressFields(ProcurementRequest existingRequest, ProcurementRequest requestDetails) {
        if (requestDetails.getAssignedTo() != null) {
            User assignedUser = userRepository.findByIdAndIsDeletedFalse(requestDetails.getAssignedTo().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));
            existingRequest.setAssignedTo(assignedUser);
        }
        if (requestDetails.getRequiresApproval() != null) {
            existingRequest.setRequiresApproval(requestDetails.getRequiresApproval());
        }
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

    private void validateProcurementRequestForUpdate(ProcurementRequest requestDetails, ProcurementRequest existingRequest) {
        // Validate justification length if provided
        if (requestDetails.getJustification() != null && requestDetails.getJustification().length() > 1000) {
            throw new ValidationException("Justification cannot exceed 1000 characters");
        }

        // Validate expected delivery date is not in the past
        if (requestDetails.getExpectedDeliveryDate() != null &&
                requestDetails.getExpectedDeliveryDate().isBefore(LocalDate.now())) {
            throw new ValidationException("Expected delivery date cannot be in the past");
        }
    }

    private void validateLineItems(List<ProcurementLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new ValidationException("At least one line item is required");
        }

        for (ProcurementLineItem lineItem : lineItems) {
            if (lineItem.getMaterial() == null || lineItem.getMaterial().getId() == null) {
                throw new ValidationException("Material is required for all line items");
            }
            if (lineItem.getRequestedQuantity() == null || lineItem.getRequestedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Requested quantity must be greater than zero");
            }

            // Validate material exists
            materialRepository.findByIdActive(lineItem.getMaterial().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Material not found with id: " + lineItem.getMaterial().getId()));
        }

        // Check for duplicate materials within the request
        Set<Long> materialIds = lineItems.stream()
                .map(item -> item.getMaterial().getId())
                .collect(Collectors.toSet());

        if (materialIds.size() != lineItems.size()) {
            throw new ValidationException("Duplicate materials found in the request");
        }
    }

    private void validateNoDuplicateRequest(ProcurementRequest request) {
        // Check for duplicate requests from same factory in last 5 minutes
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        List<ProcurementRequest> recentRequests = procurementRequestRepository.findByFactoryIdAndIsDeletedFalse(request.getFactory().getId())
                .stream()
                .filter(r -> r.getCreatedAt().isAfter(fiveMinutesAgo))
                .collect(Collectors.toList());

        for (ProcurementRequest recentRequest : recentRequests) {
            if (hasSameMaterialsAndQuantities(request, recentRequest)) {
                throw new ValidationException(
                        "A similar request with the same materials and quantities was created recently. " +
                                "Please check request: " + recentRequest.getRequestNumber()
                );
            }
        }
    }

    private boolean hasSameMaterialsAndQuantities(ProcurementRequest request1, ProcurementRequest request2) {
        if (request1.getLineItems().size() != request2.getLineItems().size()) {
            return false;
        }

        Map<Long, BigDecimal> request1Items = request1.getLineItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMaterial().getId(),
                        ProcurementLineItem::getRequestedQuantity
                ));

        Map<Long, BigDecimal> request2Items = request2.getLineItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMaterial().getId(),
                        ProcurementLineItem::getRequestedQuantity
                ));

        return request1Items.equals(request2Items);
    }

    private String generateRequestNumber(Factory factory) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String factoryCode = factory.getFactoryCode();

        String prefix = ProjectConstants.REQUEST_NUMBER_PREFIX + "-" + factoryCode + "-" + year + "-";

        // Get count of requests for this factory this year
        LocalDateTime startOfYear = LocalDateTime.of(LocalDateTime.now().getYear(), 1, 1, 0, 0);
        long count = procurementRequestRepository.countByFactoryIdAndCreatedAtAfter(factory.getId(), startOfYear) + 1;
        String sequence = String.format("%03d", count);

        return prefix + sequence;
    }

    // Dashboard and Summary Methods
    public ApiResponse<Map<String, Object>> getDashboardSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            UserRole userRole = SecurityUtil.getCurrentUserRole();

            if (userRole == UserRole.FACTORY_USER) {
                summary = getFactoryUserDashboard();
            } else if (userRole == UserRole.PURCHASE_TEAM) {
                summary = getPurchaseTeamDashboard();
            } else if (userRole == UserRole.MANAGEMENT) {
                summary = getManagementDashboard();
            } else if (userRole == UserRole.ADMIN) {
                summary = getAdminDashboard();
            }

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, summary);
        } catch (Exception e) {
            log.error("Error fetching dashboard summary", e);
            return ApiResponse.error("Failed to fetch dashboard summary");
        }
    }

    private Map<String, Object> getFactoryUserDashboard() {
        Map<String, Object> summary = new HashMap<>();
        List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();

        if (accessibleFactoryIds.isEmpty()) {
            return summary;
        }

        // Get requests for assigned factories only
        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted()
                .and(ProcurementRequestSpecification.accessibleByFactories(accessibleFactoryIds));

        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("totalRequests", allRequests.size());
        summary.put("draftRequests", countByStatus(allRequests, ProcurementStatus.DRAFT));
        summary.put("submittedRequests", countByStatus(allRequests, ProcurementStatus.SUBMITTED));
        summary.put("inProgressRequests", countByStatus(allRequests, ProcurementStatus.IN_PROGRESS));
        summary.put("receivedRequests", countByStatus(allRequests, ProcurementStatus.RECEIVED));
        summary.put("closedRequests", countByStatus(allRequests, ProcurementStatus.CLOSED));

        return summary;
    }

    private Map<String, Object> getPurchaseTeamDashboard() {
        Map<String, Object> summary = new HashMap<>();

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted();
        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("totalRequests", allRequests.size());
        summary.put("unassignedRequests", countUnassigned(allRequests));
        summary.put("myAssignedRequests", countAssignedToCurrentUser(allRequests));
        summary.put("inProgressRequests", countByStatus(allRequests, ProcurementStatus.IN_PROGRESS));
        summary.put("orderedRequests", countByStatus(allRequests, ProcurementStatus.ORDERED));
        summary.put("dispatchedRequests", countByStatus(allRequests, ProcurementStatus.DISPATCHED));

        return summary;
    }

    private Map<String, Object> getManagementDashboard() {
        Map<String, Object> summary = getPurchaseTeamDashboard();

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted();
        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("requestsRequiringApproval", countRequiringApproval(allRequests));
        summary.put("approvedByMe", countApprovedByCurrentUser(allRequests));

        return summary;
    }

    private Map<String, Object> getAdminDashboard() {
        Map<String, Object> summary = new HashMap<>();

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.isNotDeleted();
        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("totalRequests", allRequests.size());
        summary.put("totalUsers", userRepository.countByIsDeletedFalse());
        summary.put("totalFactories", factoryRepository.countByIsDeletedFalse());
        summary.put("totalMaterials", materialRepository.countByIsDeletedFalse());
        summary.put("totalVendors", vendorRepository.countByIsDeletedFalse());

        // Status breakdown
        for (ProcurementStatus status : ProcurementStatus.values()) {
            summary.put(status.name().toLowerCase() + "Requests", countByStatus(allRequests, status));
        }

        return summary;
    }

    private long countByStatus(List<ProcurementRequest> requests, ProcurementStatus status) {
        return requests.stream().filter(r -> r.getStatus() == status).count();
    }

    private long countUnassigned(List<ProcurementRequest> requests) {
        return requests.stream().filter(r -> r.getAssignedTo() == null && r.getStatus() == ProcurementStatus.SUBMITTED).count();
    }

    private long countAssignedToCurrentUser(List<ProcurementRequest> requests) {
        String currentUsername = SecurityUtil.getCurrentUsername();
        return requests.stream().filter(r -> r.getAssignedTo() != null &&
                currentUsername.equals(r.getAssignedTo().getUsername())).count();
    }

    private long countRequiringApproval(List<ProcurementRequest> requests) {
        return requests.stream().filter(ProcurementRequest::getRequiresApproval).count();
    }

    private long countApprovedByCurrentUser(List<ProcurementRequest> requests) {
        String currentUsername = SecurityUtil.getCurrentUsername();
        return requests.stream().filter(r -> r.getApprovedBy() != null &&
                currentUsername.equals(r.getApprovedBy().getUsername())).count();
    }
}