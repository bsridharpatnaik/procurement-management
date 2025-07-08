package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.ReturnRequest;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.entity.enums.ReturnStatus;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.ProcurementLineItemRepository;
import com.sungroup.procurement.repository.ReturnRequestRepository;
import com.sungroup.procurement.repository.UserRepository;
import com.sungroup.procurement.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestService {

    private final ReturnRequestRepository returnRequestRepository;
    private final ProcurementLineItemRepository lineItemRepository;
    private final UserRepository userRepository;

    // READ Operations with Factory Access Control
    public ApiResponse<List<ReturnRequest>> getAllReturnRequests() {
        try {
            List<ReturnRequest> returnRequests;

            if (SecurityUtil.isCurrentUserFactoryUser()) {
                // Factory users can only see returns for their assigned factories
                List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
                if (accessibleFactoryIds.isEmpty()) {
                    return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, Collections.emptyList());
                }
                returnRequests = returnRequestRepository.findByFactoryIds(accessibleFactoryIds);
            } else {
                // Purchase team and management can see all returns
                returnRequests = returnRequestRepository.findAllActive();
            }

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, returnRequests);
        } catch (Exception e) {
            log.error("Error fetching return requests", e);
            return ApiResponse.error("Failed to fetch return requests: " + e.getMessage());
        }
    }

    public ApiResponse<List<ReturnRequest>> getPendingReturnRequests() {
        try {
            List<ReturnRequest> pendingReturns;

            if (SecurityUtil.isCurrentUserFactoryUser()) {
                // Factory users can only see pending returns for their assigned factories
                List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
                if (accessibleFactoryIds.isEmpty()) {
                    return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, Collections.emptyList());
                }
                pendingReturns = returnRequestRepository.findByFactoryIdsAndStatus(
                        accessibleFactoryIds, ReturnStatus.RETURN_REQUESTED);
            } else {
                // Purchase team and management can see all pending returns
                pendingReturns = returnRequestRepository.findByReturnStatusAndIsDeletedFalse(ReturnStatus.RETURN_REQUESTED);
            }

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, pendingReturns);
        } catch (Exception e) {
            log.error("Error fetching pending return requests", e);
            return ApiResponse.error("Failed to fetch pending return requests: " + e.getMessage());
        }
    }

    public ApiResponse<ReturnRequest> findById(Long id) {
        try {
            ReturnRequest returnRequest = returnRequestRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException("Return request not found"));

            // Validate factory access
            Long factoryId = returnRequest.getProcurementLineItem().getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "view return request");

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, returnRequest);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching return request by id: {}", id, e);
            return ApiResponse.error("Failed to fetch return request");
        }
    }

    public ApiResponse<List<ReturnRequest>> findByLineItemId(Long lineItemId) {
        try {
            // First validate access to the line item
            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            Long factoryId = lineItem.getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "view line item returns");

            List<ReturnRequest> returnRequests = returnRequestRepository.findByProcurementLineItemIdAndIsDeletedFalse(lineItemId);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, returnRequests);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching return requests for line item: {}", lineItemId, e);
            return ApiResponse.error("Failed to fetch return requests for line item");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<ReturnRequest> createReturnRequest(Long lineItemId, BigDecimal returnQuantity, String returnReason) {
        try {
            // Validate user permissions - only factory users can create return requests
            if (!SecurityUtil.isCurrentUserFactoryUser()) {
                throw new SecurityException("Only factory users can create return requests");
            }

            // Validate line item exists and user has access
            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            Long factoryId = lineItem.getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "create return request");

            // Validate return request creation rules
            validateReturnRequestCreation(lineItem, returnQuantity, returnReason);

            // Create return request
            User currentUser = SecurityUtil.getCurrentUser();
            ReturnRequest returnRequest = new ReturnRequest();
            returnRequest.setProcurementLineItem(lineItem);
            returnRequest.setReturnQuantity(returnQuantity);
            returnRequest.setReturnReason(returnReason.trim());
            returnRequest.setReturnStatus(ReturnStatus.RETURN_REQUESTED);
            returnRequest.setRequestedBy(currentUser);

            ReturnRequest savedReturnRequest = returnRequestRepository.save(returnRequest);

            // Update line item return flags
            updateLineItemReturnFlags(lineItem);

            log.info("Return request created successfully for line item: {} by user: {}",
                    lineItemId, currentUser.getUsername());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedReturnRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating return request for line item: {}", lineItemId, e);
            return ApiResponse.error("Failed to create return request");
        }
    }

    // APPROVAL Operations
    @Transactional
    public ApiResponse<ReturnRequest> approveReturnRequest(Long returnRequestId, Long approverId) {
        try {
            // Validate user permissions - only purchase team and management can approve
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team and management can approve return requests");
            }

            // Find return request
            ReturnRequest returnRequest = returnRequestRepository.findByIdAndIsDeletedFalse(returnRequestId)
                    .orElseThrow(() -> new EntityNotFoundException("Return request not found"));

            // Validate factory access
            Long factoryId = returnRequest.getProcurementLineItem().getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "approve return request");

            // Validate approval rules
            validateReturnRequestApproval(returnRequest);

            // Find approver
            User approver = userRepository.findByIdAndIsDeletedFalse(approverId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // Approve return request
            returnRequest.setReturnStatus(ReturnStatus.RETURN_APPROVED);
            returnRequest.setApprovedBy(approver);

            ReturnRequest approvedReturnRequest = returnRequestRepository.save(returnRequest);

            // Update line item totals
            ProcurementLineItem lineItem = returnRequest.getProcurementLineItem();
            updateLineItemReturnTotals(lineItem);

            log.info("Return request approved: {} by user: {}",
                    returnRequestId, approver.getUsername());

            return ApiResponse.success("Return request approved successfully", approvedReturnRequest);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error approving return request: {}", returnRequestId, e);
            return ApiResponse.error("Failed to approve return request");
        }
    }

    // UTILITY Methods
    private void validateReturnRequestCreation(ProcurementLineItem lineItem, BigDecimal returnQuantity, String returnReason) {
        // Validate line item status
        if (lineItem.getStatus() != LineItemStatus.RECEIVED) {
            throw new ValidationException("Can only create return requests for received line items");
        }

        // Validate actual quantity exists
        if (lineItem.getActualQuantity() == null || lineItem.getActualQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Line item has no received quantity to return");
        }

        // Validate return quantity
        if (returnQuantity == null || returnQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Return quantity must be greater than zero");
        }

        // Check if this line item already has a return request
        long existingReturnsCount = returnRequestRepository.countByLineItemId(lineItem.getId());
        if (existingReturnsCount > 0) {
            throw new ValidationException("This line item already has a return request");
        }

        // Validate return quantity doesn't exceed available quantity
        BigDecimal maxReturnable = lineItem.getMaxReturnableQuantity();
        if (returnQuantity.compareTo(maxReturnable) > 0) {
            throw new ValidationException("Return quantity cannot exceed available quantity: " + maxReturnable);
        }

        // Validate return reason
        if (returnReason == null || returnReason.trim().isEmpty()) {
            throw new ValidationException("Return reason is required");
        }

        if (returnReason.trim().length() > 1000) {
            throw new ValidationException("Return reason cannot exceed 1000 characters");
        }
    }

    private void validateReturnRequestApproval(ReturnRequest returnRequest) {
        // Validate return request status
        if (returnRequest.getReturnStatus() != ReturnStatus.RETURN_REQUESTED) {
            throw new ValidationException("Only pending return requests can be approved");
        }

        // Validate line item is still in valid state
        ProcurementLineItem lineItem = returnRequest.getProcurementLineItem();
        if (lineItem.getStatus() != LineItemStatus.RECEIVED) {
            throw new ValidationException("Line item is no longer in received status");
        }

        // Re-validate return quantity is still valid (in case of concurrent modifications)
        BigDecimal currentMaxReturnable = lineItem.getMaxReturnableQuantity();
        if (returnRequest.getReturnQuantity().compareTo(currentMaxReturnable) > 0) {
            throw new ValidationException("Return quantity is no longer valid due to other returns");
        }
    }

    private void updateLineItemReturnFlags(ProcurementLineItem lineItem) {
        // Check if line item has any return requests
        long returnCount = returnRequestRepository.countByLineItemId(lineItem.getId());
        lineItem.setHasReturns(returnCount > 0);

        lineItemRepository.save(lineItem);
    }

    @Transactional
    public void updateLineItemReturnTotals(ProcurementLineItem lineItem) {
        // Calculate total approved returns
        BigDecimal totalApprovedReturns = returnRequestRepository.getTotalApprovedReturnQuantityByLineItem(lineItem.getId());

        lineItem.setTotalReturnedQuantity(totalApprovedReturns != null ? totalApprovedReturns : BigDecimal.ZERO);
        lineItem.setHasReturns(totalApprovedReturns != null && totalApprovedReturns.compareTo(BigDecimal.ZERO) > 0);

        lineItemRepository.save(lineItem);
    }

    /**
     * Check if a procurement request can be closed considering return requests
     */
    public boolean canCloseProcurementRequest(Long procurementRequestId) {
        // Find all line items for the request
        List<ProcurementLineItem> lineItems = lineItemRepository.findByProcurementRequestIdAndIsDeletedFalse(procurementRequestId);

        // Check if any line item has pending returns
        for (ProcurementLineItem lineItem : lineItems) {
            long pendingReturns = returnRequestRepository.countByLineItemIdAndStatus(
                    lineItem.getId(), ReturnStatus.RETURN_REQUESTED);
            if (pendingReturns > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get summary of return requests for a procurement request
     */
    public ApiResponse<Map<String, Object>> getReturnSummaryForRequest(Long procurementRequestId) {
        try {
            // Validate access to the procurement request
            List<ProcurementLineItem> lineItems = lineItemRepository.findByProcurementRequestIdAndIsDeletedFalse(procurementRequestId);

            if (!lineItems.isEmpty()) {
                Long factoryId = lineItems.get(0).getProcurementRequest().getFactory().getId();
                SecurityUtil.validateFactoryAccess(factoryId, "view return summary");
            }

            Map<String, Object> summary = new HashMap<>();
            long totalReturns = 0;
            long pendingReturns = 0;
            long approvedReturns = 0;
            BigDecimal totalReturnedQuantity = BigDecimal.ZERO;

            for (ProcurementLineItem lineItem : lineItems) {
                List<ReturnRequest> returns = returnRequestRepository.findByProcurementLineItemIdAndIsDeletedFalse(lineItem.getId());

                for (ReturnRequest returnRequest : returns) {
                    totalReturns++;
                    if (returnRequest.getReturnStatus() == ReturnStatus.RETURN_REQUESTED) {
                        pendingReturns++;
                    } else if (returnRequest.getReturnStatus() == ReturnStatus.RETURN_APPROVED) {
                        approvedReturns++;
                        totalReturnedQuantity = totalReturnedQuantity.add(returnRequest.getReturnQuantity());
                    }
                }
            }

            summary.put("totalReturns", totalReturns);
            summary.put("pendingReturns", pendingReturns);
            summary.put("approvedReturns", approvedReturns);
            summary.put("totalReturnedQuantity", totalReturnedQuantity);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, summary);
        } catch (SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching return summary for request: {}", procurementRequestId, e);
            return ApiResponse.error("Failed to fetch return summary");
        }
    }

    /**
     * Get pending return requests count for a line item
     */
    public long getPendingReturnRequestsCount(Long lineItemId) {
        return returnRequestRepository.countByLineItemIdAndStatus(lineItemId, ReturnStatus.RETURN_REQUESTED);
    }

    /**
     * Get all return requests for current user's factories
     */
    public ApiResponse<List<ReturnRequest>> getMyFactoryReturnRequests() {
        try {
            if (!SecurityUtil.isCurrentUserFactoryUser()) {
                return ApiResponse.error("This endpoint is only for factory users");
            }

            List<Long> accessibleFactoryIds = SecurityUtil.getCurrentUserAccessibleFactoryIds();
            if (accessibleFactoryIds.isEmpty()) {
                return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, Collections.emptyList());
            }

            List<ReturnRequest> returnRequests = returnRequestRepository.findByFactoryIds(accessibleFactoryIds);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, returnRequests);
        } catch (Exception e) {
            log.error("Error fetching factory return requests", e);
            return ApiResponse.error("Failed to fetch factory return requests: " + e.getMessage());
        }
    }
}