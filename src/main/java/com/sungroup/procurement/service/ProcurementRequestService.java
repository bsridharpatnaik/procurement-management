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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    // READ Operations
    public ApiResponse<List<ProcurementRequest>> findRequestsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<ProcurementRequest> spec = buildProcurementRequestSpecification(filterData);
            Page<ProcurementRequest> requestPage = procurementRequestRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(requestPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, requestPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching procurement requests with filters", e);
            return ApiResponse.error("Failed to fetch procurement requests: " + e.getMessage());
        }
    }

    public ApiResponse<ProcurementRequest> findById(Long id) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, request);
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

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, request);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching procurement request by number: {}", requestNumber, e);
            return ApiResponse.error("Failed to fetch procurement request");
        }
    }

    public ApiResponse<List<ProcurementRequest>> findUnassignedRequests() {
        try {
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
            validateProcurementRequestForCreate(request);

            // Generate request number
            String requestNumber = generateRequestNumber(request.getFactory());
            request.setRequestNumber(requestNumber);

            // Validate and set factory
            Factory factory = factoryRepository.findByIdActive(request.getFactory().getId())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.FACTORY_NOT_FOUND));
            request.setFactory(factory);

            // Validate line items
            validateLineItems(request.getLineItems());

            ProcurementRequest savedRequest = procurementRequestRepository.save(request);
            log.info("Procurement request created successfully: {}", savedRequest.getRequestNumber());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedRequest);
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating procurement request", e);
            return ApiResponse.error("Failed to create procurement request");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<ProcurementRequest> updateProcurementRequest(Long id, ProcurementRequest requestDetails) {
        try {
            ProcurementRequest existingRequest = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            validateProcurementRequestForUpdate(requestDetails, existingRequest);

            // Update basic fields
            existingRequest.setPriority(requestDetails.getPriority());
            existingRequest.setExpectedDeliveryDate(requestDetails.getExpectedDeliveryDate());
            existingRequest.setJustification(requestDetails.getJustification());

            // Update assigned user if provided
            if (requestDetails.getAssignedTo() != null) {
                User assignedUser = userRepository.findByIdAndIsDeletedFalse(requestDetails.getAssignedTo().getId())
                        .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));
                existingRequest.setAssignedTo(assignedUser);
            }

            ProcurementRequest updatedRequest = procurementRequestRepository.save(existingRequest);
            log.info("Procurement request updated successfully: {}", updatedRequest.getRequestNumber());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedRequest);
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating procurement request with id: {}", id, e);
            return ApiResponse.error("Failed to update procurement request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> updateStatus(Long id, ProcurementStatus newStatus) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            validateStatusTransition(request.getStatus(), newStatus);

            request.setStatus(newStatus);
            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request status updated: {} -> {}", request.getRequestNumber(), newStatus);
            return ApiResponse.success("Status updated successfully", updatedRequest);
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating status for request id: {}", id, e);
            return ApiResponse.error("Failed to update status");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> assignToUser(Long id, Long userId) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            User user = userRepository.findByIdAndIsDeletedFalse(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            request.setAssignedTo(user);
            if (request.getStatus() == ProcurementStatus.SUBMITTED) {
                request.setStatus(ProcurementStatus.IN_PROGRESS);
            }

            ProcurementRequest updatedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request assigned: {} -> {}", request.getRequestNumber(), user.getUsername());
            return ApiResponse.success("Request assigned successfully", updatedRequest);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning request id: {} to user id: {}", id, userId, e);
            return ApiResponse.error("Failed to assign request");
        }
    }

    @Transactional
    public ApiResponse<ProcurementRequest> approveRequest(Long id, Long approverId) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            if (!request.getRequiresApproval()) {
                throw new ValidationException("Request does not require approval");
            }

            User approver = userRepository.findByIdAndIsDeletedFalse(approverId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            request.setApprovedBy(approver);
            request.setApprovedDate(LocalDateTime.now());
            request.setRequiresApproval(false);

            ProcurementRequest approvedRequest = procurementRequestRepository.save(request);

            log.info("Procurement request approved: {} by {}", request.getRequestNumber(), approver.getUsername());
            return ApiResponse.success("Request approved successfully", approvedRequest);
        } catch (EntityNotFoundException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error approving request id: {}", id, e);
            return ApiResponse.error("Failed to approve request");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteProcurementRequest(Long id) {
        try {
            ProcurementRequest request = procurementRequestRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

            // Can only delete draft requests
            if (request.getStatus() != ProcurementStatus.DRAFT) {
                throw new ValidationException("Can only delete draft requests");
            }

            // Soft delete
            request.setIsDeleted(true);
            procurementRequestRepository.save(request);

            log.info("Procurement request soft deleted: {}", request.getRequestNumber());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS, "Procurement request deleted successfully");
        } catch (EntityNotFoundException | ValidationException e) {
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

    private String generateRequestNumber(Factory factory) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String factoryCode = factory.getFactoryCode();

        // Find the next sequence number for this factory and year
        String prefix = ProjectConstants.REQUEST_NUMBER_PREFIX + "-" + factoryCode + "-" + year + "-";

        // This is a simplified version - in production, you might want to use a sequence table
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

    private void validateProcurementRequestForUpdate(ProcurementRequest requestDetails, ProcurementRequest existingRequest) {
        // Additional validation logic for updates can be added here
        if (existingRequest.getStatus() == ProcurementStatus.CLOSED) {
            throw new ValidationException("Cannot update closed requests");
        }
    }

    private void validateLineItems(List<ProcurementLineItem> lineItems) {
        for (ProcurementLineItem lineItem : lineItems) {
            if (lineItem.getMaterial() == null || lineItem.getMaterial().getId() == null) {
                throw new ValidationException("Material is required for all line items");
            }
            if (lineItem.getRequestedQuantity() == null || lineItem.getRequestedQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Requested quantity must be greater than zero");
            }

            // Validate material exists
            materialRepository.findByIdActive(lineItem.getMaterial().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Material not found with id: " + lineItem.getMaterial().getId()));
        }
    }

    private void validateStatusTransition(ProcurementStatus currentStatus, ProcurementStatus newStatus) {
        // Define valid status transitions
        switch (currentStatus) {
            case DRAFT:
                if (newStatus != ProcurementStatus.SUBMITTED) {
                    throw new ValidationException("Draft requests can only be submitted");
                }
                break;
            case SUBMITTED:
                if (newStatus != ProcurementStatus.IN_PROGRESS) {
                    throw new ValidationException("Submitted requests can only move to in progress");
                }
                break;
            case IN_PROGRESS:
                if (newStatus != ProcurementStatus.ORDERED) {
                    throw new ValidationException("In progress requests can only move to ordered");
                }
                break;
            case ORDERED:
                if (newStatus != ProcurementStatus.DISPATCHED) {
                    throw new ValidationException("Ordered requests can only move to dispatched");
                }
                break;
            case DISPATCHED:
                if (newStatus != ProcurementStatus.RECEIVED) {
                    throw new ValidationException("Dispatched requests can only move to received");
                }
                break;
            case RECEIVED:
                if (newStatus != ProcurementStatus.CLOSED) {
                    throw new ValidationException("Received requests can only be closed");
                }
                break;
            case CLOSED:
                throw new ValidationException("Closed requests cannot be modified");
        }
    }
}