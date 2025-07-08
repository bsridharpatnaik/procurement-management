package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.*;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.*;
import com.sungroup.procurement.util.PermissionValidator;
import com.sungroup.procurement.util.ResponseFilterUtil;
import com.sungroup.procurement.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementLineItemService {

    private final ProcurementLineItemRepository lineItemRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorRepository vendorRepository;
    private final MaterialPriceHistoryRepository priceHistoryRepository;

    // READ Operations with Access Control
    public ApiResponse<List<ProcurementLineItem>> findByProcurementRequestId(Long requestId) {
        try {
            // Validate access to the procurement request first
            ProcurementRequest request = validateAndGetProcurementRequest(requestId);

            List<ProcurementLineItem> lineItems = lineItemRepository.findByProcurementRequestIdAndIsDeletedFalse(requestId);

            // Apply vendor information filtering
            List<ProcurementLineItem> filteredLineItems = ResponseFilterUtil.filterLineItemsForUser(lineItems);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredLineItems);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching line items for request: {}", requestId, e);
            return ApiResponse.error("Failed to fetch line items");
        }
    }

    public ApiResponse<ProcurementLineItem> findById(Long id) {
        try {
            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate factory access
            Long factoryId = lineItem.getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "view line item");

            // Apply vendor information filtering
            ProcurementLineItem filteredLineItem = ResponseFilterUtil.filterLineItemForUser(lineItem);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, filteredLineItem);
        } catch (EntityNotFoundException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching line item by id: {}", id, e);
            return ApiResponse.error("Failed to fetch line item");
        }
    }

    // VENDOR ASSIGNMENT (Purchase Team Only)
    @Transactional
    public ApiResponse<ProcurementLineItem> assignVendorAndPrice(Long lineItemId, Long vendorId, BigDecimal price) {
        try {
            // Validate permissions - only purchase team can assign vendors
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can assign vendors to line items");
            }

            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate line item can be edited for vendor assignment
            PermissionValidator.validateLineItemEditPermission(lineItem.getProcurementRequest(), "VENDOR_ASSIGNMENT");

            // Validate vendor exists and is active
            Vendor vendor = vendorRepository.findByIdActive(vendorId)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.VENDOR_NOT_FOUND));

            // Validate price
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Price must be greater than zero");
            }

            // Assign vendor and price
            lineItem.setAssignedVendor(vendor);
            lineItem.setAssignedPrice(price);
            lineItem.setStatus(LineItemStatus.ORDERED);

            ProcurementLineItem savedLineItem = lineItemRepository.save(lineItem);

            // Create price history record
            createPriceHistoryRecord(lineItem, vendor, price);

            // Update parent request status if all line items are ordered
            updateParentRequestStatus(lineItem.getProcurementRequest());

            log.info("Vendor {} assigned to line item {} with price {}",
                    vendor.getName(), lineItemId, price);

            // Apply vendor information filtering before returning
            ProcurementLineItem filteredLineItem = ResponseFilterUtil.filterLineItemForUser(savedLineItem);

            return ApiResponse.success("Vendor and price assigned successfully", filteredLineItem);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning vendor to line item: {}", lineItemId, e);
            return ApiResponse.error("Failed to assign vendor to line item");
        }
    }

    // UPDATE LINE ITEM STATUS
    @Transactional
    public ApiResponse<ProcurementLineItem> updateLineItemStatus(Long lineItemId, LineItemStatus newStatus) {
        try {
            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate permissions based on status transition
            validateLineItemStatusTransition(lineItem, newStatus);

            lineItem.setStatus(newStatus);
            ProcurementLineItem savedLineItem = lineItemRepository.save(lineItem);

            // Update parent request status
            updateParentRequestStatus(lineItem.getProcurementRequest());

            log.info("Line item {} status updated to {}", lineItemId, newStatus);

            // Apply vendor information filtering before returning
            ProcurementLineItem filteredLineItem = ResponseFilterUtil.filterLineItemForUser(savedLineItem);

            return ApiResponse.success("Line item status updated successfully", filteredLineItem);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating line item status: {}", lineItemId, e);
            return ApiResponse.error("Failed to update line item status");
        }
    }

    // SHORT CLOSE OPERATIONS (Purchase Team Only)
    @Transactional
    public ApiResponse<ProcurementLineItem> shortCloseLineItem(Long lineItemId, String shortCloseReason) {
        try {
            // Validate permissions - only purchase team can short close
            if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                throw new SecurityException("Only purchase team can perform short close operations");
            }

            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate short close is allowed
            validateShortCloseOperation(lineItem, shortCloseReason);

            // Perform short close
            lineItem.setIsShortClosed(true);
            lineItem.setShortCloseReason(shortCloseReason.trim());
            lineItem.setStatus(LineItemStatus.SHORT_CLOSED);
            lineItem.setActualQuantity(BigDecimal.ZERO); // No quantity received for short closed items

            ProcurementLineItem savedLineItem = lineItemRepository.save(lineItem);

            // Update parent request status
            updateParentRequestStatus(lineItem.getProcurementRequest());

            log.info("Line item {} short closed with reason: {}", lineItemId, shortCloseReason);

            // Apply vendor information filtering before returning
            ProcurementLineItem filteredLineItem = ResponseFilterUtil.filterLineItemForUser(savedLineItem);

            return ApiResponse.success("Line item short closed successfully", filteredLineItem);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error short closing line item: {}", lineItemId, e);
            return ApiResponse.error("Failed to short close line item");
        }
    }

    // RECEIVE LINE ITEM (Factory Users Only)
    @Transactional
    public ApiResponse<ProcurementLineItem> receiveLineItem(Long lineItemId, BigDecimal actualQuantity) {
        try {
            // Validate permissions - only factory users can receive
            if (!SecurityUtil.isCurrentUserFactoryUser()) {
                throw new SecurityException("Only factory users can receive line items");
            }

            ProcurementLineItem lineItem = lineItemRepository.findByIdAndIsDeletedFalse(lineItemId)
                    .orElseThrow(() -> new EntityNotFoundException("Line item not found"));

            // Validate factory access
            Long factoryId = lineItem.getProcurementRequest().getFactory().getId();
            SecurityUtil.validateFactoryAccess(factoryId, "receive line item");

            // Validate receive operation
            validateReceiveOperation(lineItem, actualQuantity);

            // Update line item with received quantity
            lineItem.setActualQuantity(actualQuantity);
            lineItem.setStatus(LineItemStatus.RECEIVED);

            ProcurementLineItem savedLineItem = lineItemRepository.save(lineItem);

            // Update parent request status
            updateParentRequestStatus(lineItem.getProcurementRequest());

            log.info("Line item {} received with quantity: {}", lineItemId, actualQuantity);

            // Apply vendor information filtering before returning (Factory users won't see vendor info)
            ProcurementLineItem filteredLineItem = ResponseFilterUtil.filterLineItemForUser(savedLineItem);

            return ApiResponse.success("Line item received successfully", filteredLineItem);
        } catch (EntityNotFoundException | ValidationException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error receiving line item: {}", lineItemId, e);
            return ApiResponse.error("Failed to receive line item");
        }
    }

    // UTILITY METHODS
    private ProcurementRequest validateAndGetProcurementRequest(Long requestId) {
        ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.PROCUREMENT_REQUEST_NOT_FOUND));

        // Validate factory access
        SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "access procurement request");

        return request;
    }

    private void validateLineItemStatusTransition(ProcurementLineItem lineItem, LineItemStatus newStatus) {
        LineItemStatus currentStatus = lineItem.getStatus();
        ProcurementRequest request = lineItem.getProcurementRequest();

        // Validate request is not locked by approval
        if (request.getRequiresApproval() && !SecurityUtil.canApproveProcurementRequests()) {
            throw new ValidationException("Cannot update line item status while request is pending approval");
        }

        // Validate status transition based on user role and current status
        switch (newStatus) {
            case ORDERED:
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team can mark line items as ordered");
                }
                if (currentStatus != LineItemStatus.PENDING && currentStatus != LineItemStatus.IN_PROGRESS) {
                    throw new ValidationException("Invalid status transition to ORDERED");
                }
                break;

            case DISPATCHED:
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team can mark line items as dispatched");
                }
                if (currentStatus != LineItemStatus.ORDERED) {
                    throw new ValidationException("Can only dispatch ordered line items");
                }
                break;

            case RECEIVED:
                if (!SecurityUtil.isCurrentUserFactoryUser()) {
                    throw new SecurityException("Only factory users can mark line items as received");
                }
                if (currentStatus != LineItemStatus.DISPATCHED) {
                    throw new ValidationException("Can only receive dispatched line items");
                }
                break;

            default:
                throw new ValidationException("Invalid status transition");
        }
    }

    private void validateShortCloseOperation(ProcurementLineItem lineItem, String shortCloseReason) {
        // Validate reason is provided
        if (shortCloseReason == null || shortCloseReason.trim().isEmpty()) {
            throw new ValidationException("Short close reason is required");
        }

        if (shortCloseReason.trim().length() > 500) {
            throw new ValidationException("Short close reason cannot exceed 500 characters");
        }

        // Validate line item can be short closed
        if (lineItem.getIsShortClosed()) {
            throw new ValidationException("Line item is already short closed");
        }

        // Can only short close items that are pending, in progress, or ordered
        LineItemStatus status = lineItem.getStatus();
        if (status == LineItemStatus.DISPATCHED || status == LineItemStatus.RECEIVED || status == LineItemStatus.SHORT_CLOSED) {
            throw new ValidationException("Cannot short close line item in " + status + " status");
        }

        // Validate request is not locked by approval
        ProcurementRequest request = lineItem.getProcurementRequest();
        if (request.getRequiresApproval() && !SecurityUtil.canApproveProcurementRequests()) {
            throw new ValidationException("Cannot short close while request is pending approval");
        }
    }

    private void validateReceiveOperation(ProcurementLineItem lineItem, BigDecimal actualQuantity) {
        // Validate quantity
        if (actualQuantity == null || actualQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Actual quantity cannot be negative");
        }

        if (actualQuantity.compareTo(lineItem.getRequestedQuantity()) > 0) {
            throw new ValidationException("Actual quantity cannot exceed requested quantity without approval");
        }

        // Validate line item status
        if (lineItem.getStatus() != LineItemStatus.DISPATCHED) {
            throw new ValidationException("Can only receive dispatched line items");
        }

        // Validate not already received
        if (lineItem.getActualQuantity() != null) {
            throw new ValidationException("Line item has already been received");
        }
    }

    private void createPriceHistoryRecord(ProcurementLineItem lineItem, Vendor vendor, BigDecimal price) {
        try {
            MaterialPriceHistory priceHistory = new MaterialPriceHistory();
            priceHistory.setMaterial(lineItem.getMaterial());
            priceHistory.setVendor(vendor);
            priceHistory.setPrice(price);
            priceHistory.setProcurementLineItem(lineItem);

            priceHistoryRepository.save(priceHistory);
            log.debug("Price history record created for material {} and vendor {}",
                    lineItem.getMaterial().getName(), vendor.getName());
        } catch (Exception e) {
            log.error("Failed to create price history record", e);
            // Don't fail the main operation for price history creation failure
        }
    }

    /**
     * Update parent request status based on line item statuses
     */
    private void updateParentRequestStatus(ProcurementRequest request) {
        try {
            List<ProcurementLineItem> lineItems = request.getLineItems();
            if (lineItems == null || lineItems.isEmpty()) {
                return;
            }

            // Calculate new status based on line item statuses
            ProcurementStatus newStatus = calculateRequestStatus(lineItems);

            // Update if status has changed
            if (newStatus != request.getStatus()) {
                request.setStatus(newStatus);
                procurementRequestRepository.save(request);
                log.info("Updated request {} status to {}", request.getRequestNumber(), newStatus);
            }
        } catch (Exception e) {
            log.error("Error updating parent request status", e);
            // Don't fail the main operation for status update failure
        }
    }

    /**
     * Calculate procurement request status based on line item statuses
     */
    private ProcurementStatus calculateRequestStatus(List<ProcurementLineItem> lineItems) {
        long totalItems = lineItems.size();
        long pendingItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.PENDING ? 1 : 0).sum();
        long inProgressItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.IN_PROGRESS ? 1 : 0).sum();
        long orderedItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.ORDERED ? 1 : 0).sum();
        long dispatchedItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.DISPATCHED ? 1 : 0).sum();
        long receivedItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.RECEIVED ? 1 : 0).sum();
        long shortClosedItems = lineItems.stream().mapToLong(item ->
                item.getStatus() == LineItemStatus.SHORT_CLOSED ? 1 : 0).sum();

        // All items received or short closed
        if ((receivedItems + shortClosedItems) == totalItems) {
            return ProcurementStatus.RECEIVED;
        }

        // Some items dispatched
        if (dispatchedItems > 0) {
            return ProcurementStatus.DISPATCHED;
        }

        // Some items ordered
        if (orderedItems > 0) {
            return ProcurementStatus.ORDERED;
        }

        // Some items in progress
        if (inProgressItems > 0 || orderedItems > 0) {
            return ProcurementStatus.IN_PROGRESS;
        }

        // Default to current status if no clear pattern
        return ProcurementStatus.IN_PROGRESS;
    }
}