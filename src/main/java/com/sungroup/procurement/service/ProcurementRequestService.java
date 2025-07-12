package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.*;
import com.sungroup.procurement.entity.enums.*;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
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
    private final ReturnRequestRepository returnRequestRepository;
    private final FilterService filterService;
    private final ReturnRequestService returnRequestService;
    private final MaterialVendorHistoryService materialVendorHistoryService;
    private final PurchaseHistoryService purchaseHistoryService;

    public ApiResponse<List<ProcurementRequest>> findRequestsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and(buildProcurementRequestSpecification(filterData));

            Page<ProcurementRequest> requestPage = procurementRequestRepository.findAll(spec, pageable);
            List<ProcurementRequest> filteredRequests = ResponseFilterUtil.filterProcurementRequestsForUser(requestPage.getContent());
            PaginationResponse pagination = PaginationResponse.from(requestPage);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredRequests, pagination);
        } catch (Exception e) {
            log.error("Error fetching procurement requests with filters", e);
            return ApiResponse.error("Failed to fetch procurement requests: " + e.getMessage());
        }
    }

    public ApiResponse<ProcurementRequest> findById(Long id) {
        try {
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
                    .and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id));

            Optional<ProcurementRequest> requestOpt = procurementRequestRepository.findOne(spec);
            ProcurementRequest request = requestOpt.orElseThrow(() ->
                    new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

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
            ProcurementRequest request = procurementRequestRepository.findByRequestNumberAndIsDeletedFalse(requestNumber)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);
            ProcurementRequest filteredRequest = ResponseFilterUtil.filterProcurementRequestForUser(request);
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

            // SECURE QUERY: Include factory access control even for purchase team
            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted()
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

            Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();

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
            validateNoDuplicateRequest(request);

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

            // Validate factory access
            validateFactoryAccess(request);

            // FIXED: Validate status - can only mark SUBMITTED or IN_PROGRESS for approval
            if (request.getStatus() != ProcurementStatus.SUBMITTED &&
                    request.getStatus() != ProcurementStatus.IN_PROGRESS) {
                throw new ValidationException("Can only mark submitted or in-progress requests for approval");
            }

            // FIXED: Prevent marking if already requires approval
            if (request.getRequiresApproval()) {
                throw new ValidationException("Request already requires approval");
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
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate permissions
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can close requests");
            }

            // Validate factory access
            validateFactoryAccess(request);

            // FIXED: Validate all line items are received or short closed
            if (request.getLineItems() != null) {
                boolean hasIncompleteItems = request.getLineItems().stream()
                        .filter(item -> !item.getIsDeleted())
                        .anyMatch(item -> item.getStatus() != LineItemStatus.RECEIVED &&
                                item.getStatus() != LineItemStatus.SHORT_CLOSED);

                if (hasIncompleteItems) {
                    throw new ValidationException("Cannot close request with incomplete line items. " +
                            "All items must be received or short closed.");
                }

                // FIXED: Check for pending returns
                for (ProcurementLineItem lineItem : request.getLineItems()) {
                    if (!lineItem.getIsDeleted()) {
                        long pendingReturns = returnRequestRepository.countByLineItemIdAndStatus(
                                lineItem.getId(), ReturnStatus.RETURN_REQUESTED);
                        if (pendingReturns > 0) {
                            throw new ValidationException("Cannot close request with pending return requests. " +
                                    "All returns must be approved first.");
                        }
                    }
                }
            }

            // Validate current status allows closure
            if (request.getStatus() != ProcurementStatus.RECEIVED) {
                throw new ValidationException("Only received requests can be closed");
            }

            request.setStatus(ProcurementStatus.CLOSED);
            ProcurementRequest closedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request closed: {}", request.getRequestNumber());
            return ApiResponse.success("Request closed successfully", closedRequest);

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
        if (statuses != null && !statuses.isEmpty())
            spec = spec.and(ProcurementRequestSpecification.hasStatuses(statuses));
        if (priorities != null && !priorities.isEmpty())
            spec = spec.and(ProcurementRequestSpecification.hasPriorities(priorities));
        if (factoryIds != null && !factoryIds.isEmpty())
            spec = spec.and(ProcurementRequestSpecification.hasFactoryIds(factoryIds));
        if (factoryName != null) spec = spec.and(ProcurementRequestSpecification.hasFactoryName(factoryName));
        if (assignedToIds != null && !assignedToIds.isEmpty())
            spec = spec.and(ProcurementRequestSpecification.isAssignedToUsers(assignedToIds));
        if (requiresApproval != null)
            spec = spec.and(ProcurementRequestSpecification.requiresApproval(requiresApproval));
        if (approvedByIds != null && !approvedByIds.isEmpty())
            spec = spec.and(ProcurementRequestSpecification.isApprovedBy(approvedByIds.get(0)));
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
        // FIXED: Prevent editing when approval is required
        if (existingRequest.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Cannot edit request while it requires management approval. " +
                    "Request must be approved first.");
        }

        ProcurementStatus currentStatus = existingRequest.getStatus();
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // Validate edit permissions based on status and role
        switch (currentStatus) {
            case DRAFT:
                // Only factory user (creator) can edit
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can edit draft requests");
                }
                if (!existingRequest.getCreatedBy().equals(currentUsername)) {
                    throw new SecurityException("Only the creator can edit draft requests");
                }
                break;

            case SUBMITTED:
                // Only purchase team can edit (limited fields)
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team can edit submitted requests");
                }
                validateSubmittedEditFields(requestDetails);
                break;

            case IN_PROGRESS:
                // Only purchase team can edit (limited fields)
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team can edit in-progress requests");
                }
                validateInProgressEditFields(requestDetails);
                break;

            case ORDERED:
            case DISPATCHED:
                // Only status updates allowed
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team can edit ordered/dispatched requests");
                }
                validateOrderedDispatchedEditFields(requestDetails);
                break;

            case RECEIVED:
                // Only factory users can mark as received
                if (!SecurityUtil.isCurrentUserFactoryUser()) {
                    throw new SecurityException("Only factory users can edit received requests");
                }
                validateReceivedEditFields(requestDetails);
                break;

            case CLOSED:
                throw new ValidationException("Closed requests cannot be modified");

            default:
                throw new ValidationException("Invalid request status for editing");
        }
    }

    private void validateSubmittedEditFields(ProcurementRequest requestDetails) {
        // Only assignedTo can be changed in SUBMITTED status
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                (requestDetails.getLineItems() != null && !requestDetails.getLineItems().isEmpty())) {
            throw new ValidationException("Only assignment can be changed in submitted requests");
        }
    }

    private void validateInProgressEditFields(ProcurementRequest requestDetails) {
        // Limited fields can be changed in IN_PROGRESS status
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                (requestDetails.getLineItems() != null && !requestDetails.getLineItems().isEmpty())) {
            throw new ValidationException("Only assignment and approval flag can be changed in in-progress requests");
        }
    }

    private void validateOrderedDispatchedEditFields(ProcurementRequest requestDetails) {
        // Only status can be updated in ORDERED/DISPATCHED
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                requestDetails.getAssignedTo() != null ||
                requestDetails.getRequiresApproval() != null ||
                (requestDetails.getLineItems() != null && !requestDetails.getLineItems().isEmpty())) {
            throw new ValidationException("Only status can be updated in ordered/dispatched requests");
        }
    }

    private void validateReceivedEditFields(ProcurementRequest requestDetails) {
        // Only status to CLOSED can be updated in RECEIVED
        if (requestDetails.getJustification() != null ||
                requestDetails.getPriority() != null ||
                requestDetails.getExpectedDeliveryDate() != null ||
                requestDetails.getAssignedTo() != null ||
                requestDetails.getRequiresApproval() != null ||
                (requestDetails.getLineItems() != null && !requestDetails.getLineItems().isEmpty())) {
            throw new ValidationException("Only status can be updated in received requests");
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

        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            throw new ValidationException("At least one line item is required");
        }

        // FIXED: Add duplicate material validation
        Set<Long> materialIds = new HashSet<>();
        for (ProcurementLineItem lineItem : request.getLineItems()) {
            if (lineItem.getMaterial() == null || lineItem.getMaterial().getId() == null) {
                throw new ValidationException("Material is required for all line items");
            }

            Long materialId = lineItem.getMaterial().getId();
            if (materialIds.contains(materialId)) {
                // Get material name for better error message
                Material material = materialRepository.findById(materialId).orElse(null);
                String materialName = material != null ? material.getName() : "Unknown";
                throw new ValidationException("Duplicate material found: " + materialName +
                        ". Each material can only appear once in a procurement request.");
            }
            materialIds.add(materialId);

            if (lineItem.getRequestedQuantity() == null || lineItem.getRequestedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Requested quantity must be greater than zero");
            }
        }

        // Validate justification length if provided
        if (request.getJustification() != null && request.getJustification().length() > 1000) {
            throw new ValidationException("Justification cannot exceed 1000 characters");
        }

        // Validate expected delivery date is not in the past
        if (request.getExpectedDeliveryDate() != null && request.getExpectedDeliveryDate().isBefore(java.time.LocalDate.now())) {
            throw new ValidationException("Expected delivery date cannot be in the past");
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

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();
        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("totalRequests", allRequests.size());
        summary.put("draftRequests", countByStatus(allRequests, ProcurementStatus.DRAFT));
        summary.put("submittedRequests", countByStatus(allRequests, ProcurementStatus.SUBMITTED));
        summary.put("inProgressRequests", countByStatus(allRequests, ProcurementStatus.IN_PROGRESS));
        summary.put("receivedRequests", countByStatus(allRequests, ProcurementStatus.RECEIVED));
        summary.put("closedRequests", countByStatus(allRequests, ProcurementStatus.CLOSED));
        summary.put("cancelledRequests", countByStatus(allRequests, ProcurementStatus.CANCELLED)); // NEW

        return summary;
    }

    private Map<String, Object> getPurchaseTeamDashboard() {
        Map<String, Object> summary = new HashMap<>();

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();
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

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();
        List<ProcurementRequest> allRequests = procurementRequestRepository.findAll(spec);

        summary.put("requestsRequiringApproval", countRequiringApproval(allRequests));
        summary.put("approvedByMe", countApprovedByCurrentUser(allRequests));

        return summary;
    }

    private Map<String, Object> getAdminDashboard() {
        Map<String, Object> summary = new HashMap<>();

        Specification<ProcurementRequest> spec = ProcurementRequestSpecification.withSecurityAndNotDeleted();
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

    @Transactional
    public ApiResponse<ProcurementRequest> cancelProcurementRequest(Long id, String cancellationReason) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            // Validate cancellation permissions and business rules
            validateCancellationPermissions(request, cancellationReason);

            // Perform cancellation
            performRequestCancellation(request, cancellationReason);

            ProcurementRequest cancelledRequest = procurementRequestRepository.save(request);

            log.info("Procurement request cancelled: {} by {} with reason: {}",
                    request.getRequestNumber(),
                    SecurityUtil.getCurrentUsername(),
                    cancellationReason);

            return ApiResponse.success("Request cancelled successfully", cancelledRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error cancelling procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to cancel procurement request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> removeLineItem(Long requestId, Long lineItemId, String removalReason) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate factory access
            validateFactoryAccess(request);

            ProcurementLineItem lineItem = request.getLineItems().stream()
                    .filter(item -> item.getId().equals(lineItemId) && !item.getIsDeleted())
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate line item removal permissions
            validateLineItemRemovalPermissions(request, lineItem, removalReason);

            // Perform line item removal (soft delete)
            performLineItemRemoval(lineItem, removalReason);

            // Check if request becomes empty after removal
            long remainingItems = request.getLineItems().stream()
                    .filter(item -> !item.getIsDeleted())
                    .count();

            if (remainingItems == 0) {
                // No items left - cancel the entire request
                performRequestCancellation(request, "All line items removed: " + removalReason);
            }

            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Line item removed: Request {} Line Item {} by {} with reason: {}",
                    request.getRequestNumber(),
                    lineItemId,
                    SecurityUtil.getCurrentUsername(),
                    removalReason);

            return ApiResponse.success("Line item removed successfully", updatedRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error removing line item {} from request {}", lineItemId, requestId, e);
            return ApiResponse.error("Failed to remove line item");
        }
    }

    // VALIDATION METHODS
    private void validateCancellationPermissions(ProcurementRequest request, String cancellationReason) {
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        ProcurementStatus currentStatus = request.getStatus();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // Check if already cancelled
        if (request.getIsCancelled()) {
            throw new ValidationException("Request is already cancelled");
        }

        // Check if can be cancelled based on status
        if (!request.canBeCancelled()) {
            throw new ValidationException("Cannot cancel request in " + currentStatus + " status");
        }

        // Check if has dispatched items
        if (request.hasDispatchedItems()) {
            throw new ValidationException("Cannot cancel request with dispatched items");
        }

        // Role-based permission validation
        switch (currentStatus) {
            case DRAFT:
                // Only factory user (creator) can cancel
                if (currentUserRole != UserRole.FACTORY_USER) {
                    throw new SecurityException("Only factory users can cancel draft requests");
                }
                if (!request.getCreatedBy().equals(currentUsername)) {
                    throw new SecurityException("Only the creator can cancel draft requests");
                }
                break;

            case SUBMITTED:
            case IN_PROGRESS:
            case ORDERED:
                // Only purchase team or management can cancel
                if (currentUserRole != UserRole.PURCHASE_TEAM &&
                        currentUserRole != UserRole.MANAGEMENT &&
                        currentUserRole != UserRole.ADMIN) {
                    throw new SecurityException("Only purchase team or management can cancel " + currentStatus + " requests");
                }
                break;

            default:
                throw new ValidationException("Cannot cancel request in " + currentStatus + " status");
        }

        // Validate reason
        if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
            throw new ValidationException("Cancellation reason is required");
        }

        if (cancellationReason.trim().length() > 1000) {
            throw new ValidationException("Cancellation reason cannot exceed 1000 characters");
        }
    }

    private void validateLineItemRemovalPermissions(ProcurementRequest request,
                                                    ProcurementLineItem lineItem,
                                                    String removalReason) {
        ProcurementStatus currentStatus = request.getStatus();
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // Line item removal only allowed in early stages
        if (currentStatus != ProcurementStatus.DRAFT && currentStatus != ProcurementStatus.SUBMITTED) {
            throw new ValidationException("Line items can only be removed from DRAFT or SUBMITTED requests");
        }

        // Check if line item has been processed (vendor assigned, etc.)
        if (lineItem.getAssignedVendor() != null || lineItem.getAssignedPrice() != null) {
            throw new ValidationException("Cannot remove line item that has vendor assignment or pricing");
        }

        // Role-based permissions
        if (currentStatus == ProcurementStatus.DRAFT) {
            // Only creator can remove from draft
            if (currentUserRole != UserRole.FACTORY_USER || !request.getCreatedBy().equals(currentUsername)) {
                throw new SecurityException("Only the creator can remove line items from draft requests");
            }
        } else if (currentStatus == ProcurementStatus.SUBMITTED) {
            // Only purchase team/management can remove from submitted
            if (currentUserRole != UserRole.PURCHASE_TEAM &&
                    currentUserRole != UserRole.MANAGEMENT &&
                    currentUserRole != UserRole.ADMIN) {
                throw new SecurityException("Only purchase team can remove line items from submitted requests");
            }
        }

        // Validate reason
        if (removalReason == null || removalReason.trim().isEmpty()) {
            throw new ValidationException("Line item removal reason is required");
        }
    }

    private void performRequestCancellation(ProcurementRequest request, String cancellationReason) {
        User currentUser = SecurityUtil.getCurrentUser();

        // Cancel the request
        request.setIsCancelled(true);
        request.setCancellationReason(cancellationReason.trim());
        request.setCancelledBy(currentUser);
        request.setCancelledDate(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        request.setStatus(ProcurementStatus.CANCELLED);

        // Note: Line items remain as-is when request is cancelled
        // Their status indicates what stage they were in when request was cancelled
    }

    private void performLineItemRemoval(ProcurementLineItem lineItem, String removalReason) {
        // Soft delete the line item
        lineItem.setIsDeleted(true);

        // Could add a removal reason field if needed for audit
        // For now, we can log the reason or add it to a notes field
        log.info("Line item {} removed with reason: {}", lineItem.getId(), removalReason);
    }

    public ApiResponse<Boolean> canEditRequest(Long requestId) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                    .orElseThrow(() -> new EntityNotFoundException("Request not found"));

            // Use existing permission validation logic
            PermissionValidator.validateOperationPermission(request, "EDIT");
            return ApiResponse.success("Can edit", true);
        } catch (Exception e) {
            return ApiResponse.success("Cannot edit", false);
        }
    }

    public ApiResponse<List<ProcurementStatus>> getNextValidStatuses(Long requestId) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                    .orElseThrow(() -> new EntityNotFoundException("Request not found"));

            List<ProcurementStatus> validStatuses = new ArrayList<>();
            ProcurementStatus currentStatus = request.getStatus();

            // Based on current status and user role, determine valid next statuses
            switch (currentStatus) {
                case DRAFT:
                    if (SecurityUtil.isCurrentUserFactoryUser()) {
                        validStatuses.add(ProcurementStatus.SUBMITTED);
                    }
                    break;
                case SUBMITTED:
                    if (SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                        validStatuses.add(ProcurementStatus.IN_PROGRESS);
                    }
                    break;
                case IN_PROGRESS:
                    if (SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                        validStatuses.add(ProcurementStatus.ORDERED);
                    }
                    break;
                case ORDERED:
                    if (SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                        validStatuses.add(ProcurementStatus.DISPATCHED);
                    }
                    break;
                case DISPATCHED:
                    if (SecurityUtil.isCurrentUserFactoryUser()) {
                        validStatuses.add(ProcurementStatus.RECEIVED);
                    }
                    break;
                case RECEIVED:
                    if (SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                        validStatuses.add(ProcurementStatus.CLOSED);
                    }
                    break;
            }

            return ApiResponse.success("Next valid statuses", validStatuses);
        } catch (Exception e) {
            log.error("Error getting next valid statuses", e);
            return ApiResponse.error("Failed to get valid statuses");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> assignVendorToLineItem(Long requestId, Long lineItemId, Long vendorId, BigDecimal price) {
        try {
            // Validate that only purchase team can assign vendors
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can assign vendors");
            }

            // Validate price
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Price must be greater than zero");
            }

            ProcurementRequest request = procurementRequestRepository.findByIdActive(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Validate request status
            if (request.getStatus() != ProcurementStatus.IN_PROGRESS && request.getStatus() != ProcurementStatus.SUBMITTED) {
                throw new ValidationException("Can only assign vendors to requests in SUBMITTED or IN_PROGRESS status");
            }

            // Validate factory access
            if (SecurityUtil.isCurrentUserFactoryUser()) {
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "assign vendor");
            }

            // Find the line item
            ProcurementLineItem lineItem = request.getLineItems().stream()
                    .filter(li -> li.getId().equals(lineItemId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate line item status
            if (lineItem.getStatus() != LineItemStatus.PENDING && lineItem.getStatus() != LineItemStatus.IN_PROGRESS) {
                throw new ValidationException("Can only assign vendors to pending or in-progress line items");
            }

            // Validate vendor exists
            Vendor vendor = vendorRepository.findByIdAndIsDeletedFalse(vendorId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            // Assign vendor and price
            lineItem.setAssignedVendor(vendor);
            lineItem.setAssignedPrice(price);
            lineItem.setStatus(LineItemStatus.ORDERED);

            // Update request status if it's still SUBMITTED
            if (request.getStatus() == ProcurementStatus.SUBMITTED) {
                request.setStatus(ProcurementStatus.IN_PROGRESS);
            }

            // Save the changes
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            // Update material-vendor history
            materialVendorHistoryService.updateMaterialVendorHistory(lineItem);

            // Create purchase history record
            purchaseHistoryService.createPurchaseHistory(lineItem);

            log.info("Vendor {} assigned to line item {} in request {} with price {}",
                    vendor.getName(), lineItemId, request.getRequestNumber(), price);

            return ApiResponse.success(ProjectConstants.VENDOR_ASSIGNED_SUCCESS, updatedRequest);

        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning vendor to line item", e);
            return ApiResponse.error("Failed to assign vendor");
        }
    }

    public ApiResponse<ProcurementRequest> createAndSubmitRequest(ProcurementRequest request) {
        try {
            // Validate that only purchase team can use this method
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can create and submit requests directly");
            }

            // Validate factory exists and is active
            if (request.getFactory() == null || request.getFactory().getId() == null) {
                throw new ValidationException("Factory is required");
            }

            Factory factory = factoryRepository.findByIdAndIsDeletedFalse(request.getFactory().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));

            request.setFactory(factory);

            // Validate factory access if user is factory user (shouldn't happen for purchase team, but safety check)
            if (SecurityUtil.isCurrentUserFactoryUser()) {
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "create request");
            }

            // Use the existing validation method
            validateProcurementRequestForCreate(request);

            // Set created by current user (get the User entity, not just username)
            User currentUser = SecurityUtil.getCurrentUser();
            if (currentUser == null) {
                throw new SecurityException("Unable to determine current user");
            }
            request.setCreatedBy(currentUser.getUsername());

            // Set status directly to SUBMITTED (bypass DRAFT)
            request.setStatus(ProcurementStatus.SUBMITTED);

            // Generate request number
            String requestNumber = generateRequestNumber(request.getFactory());
            request.setRequestNumber(requestNumber);

            // Set default values
            if (request.getPriority() == null) {
                request.setPriority(Priority.MEDIUM);
            }
            request.setRequiresApproval(false);
            request.setIsShortClosed(false);

            // Save the request first
            ProcurementRequest savedRequest = procurementRequestRepository.save(request);

            // Update line items with request reference and set initial status
            if (request.getLineItems() != null && !request.getLineItems().isEmpty()) {
                for (ProcurementLineItem lineItem : request.getLineItems()) {
                    lineItem.setProcurementRequest(savedRequest);
                    lineItem.setStatus(LineItemStatus.PENDING);
                    lineItem.setIsShortClosed(false);
                    lineItem.setHasReturns(false);
                    lineItem.setTotalReturnedQuantity(BigDecimal.ZERO);
                }
            }

            // Save again to persist line item changes
            savedRequest = procurementRequestRepository.save(savedRequest);

            log.info("Purchase team created and submitted request: {} for factory: {}",
                    savedRequest.getRequestNumber(), factory.getName());

            return ApiResponse.success(ProjectConstants.REQUEST_SUBMITTED_DIRECTLY, savedRequest);

        } catch (SecurityException | ValidationException | EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating and submitting procurement request", e);
            return ApiResponse.error("Failed to create and submit request");
        }
    }
}