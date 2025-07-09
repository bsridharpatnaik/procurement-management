package com.sungroup.procurement.util;

import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive permission validation framework for procurement operations
 */
@Component
@Slf4j
public class PermissionValidator {

    /**
     * Validate if current user can edit a procurement request
     */
    public static void validateCanEditProcurementRequest(ProcurementRequest request) {
        User currentUser = SecurityUtil.getCurrentUser();
        if (currentUser == null) {
            throw new ValidationException("User not authenticated");
        }

        // FIXED: Complete approval workflow security - prevent ALL editing when requiresApproval = true
        if (request.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Request is pending management approval and cannot be edited. " +
                    "All modifications are blocked until the request is approved by management.");
        }

        // Validate based on current status
        validateStatusBasedEditPermission(request, currentUser);
    }

    /**
     * Validate specific field editing permissions
     */
    public static void validateFieldEditPermission(ProcurementRequest request, String fieldName, Object newValue) {
        ProcurementStatus status = request.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // FIXED: Block ALL field edits when requiresApproval = true (except for management approval)
        if (request.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Cannot edit field '" + fieldName +
                    "' while request is pending management approval");
        }

        switch (status) {
            case DRAFT:
                validateDraftFieldEdit(request, fieldName, currentUsername);
                break;
            case SUBMITTED:
                validateSubmittedFieldEdit(fieldName, userRole);
                break;
            case IN_PROGRESS:
                validateInProgressFieldEdit(fieldName, userRole);
                break;
            case ORDERED:
            case DISPATCHED:
                validateOrderedDispatchedFieldEdit(fieldName, userRole);
                break;
            case RECEIVED:
                validateReceivedFieldEdit(fieldName, userRole);
                break;
            case CLOSED:
                throw new ValidationException("Closed requests cannot be modified");
        }
    }

    public static void validateOperationPermission(ProcurementRequest request, String operation) {
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();
        ProcurementStatus status = request.getStatus();

        // FIXED: Block operations when requiresApproval = true (except for management)
        if (request.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Cannot perform operation '" + operation +
                    "' while request is pending management approval");
        }

        switch (operation.toUpperCase()) {
            case "EDIT":
                validateCanEditProcurementRequest(request);
                break;
            case "DELETE":
                validateDeletePermission(request);  // Now properly defined
                break;
            case "ASSIGN":
                validateAssignmentPermission(request);
                break;
            case "APPROVE":
                validateApprovalPermission(request);
                break;
            case "MARK_FOR_APPROVAL":
                validateMarkForApprovalPermission(request);
                break;
            case "VENDOR_ASSIGNMENT":
                validateVendorAssignmentPermission(request);
                break;
            case "CANCEL":
                validateCancelPermission(request);
                break;
            case "CLOSE":
                validateClosePermission(request);
                break;
            default:
                throw new ValidationException("Unknown operation: " + operation);
        }
    }

    private static void validateCancelPermission(ProcurementRequest request) {
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        ProcurementStatus currentStatus = request.getStatus();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // Check if already cancelled
        if (request.getIsCancelled()) {
            throw new ValidationException("Request is already cancelled");
        }

        // Check if can be cancelled based on status (cannot cancel dispatched/received/closed)
        if (currentStatus == ProcurementStatus.DISPATCHED ||
                currentStatus == ProcurementStatus.RECEIVED ||
                currentStatus == ProcurementStatus.CLOSED) {
            throw new ValidationException("Cannot cancel request in " + currentStatus + " status");
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
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new SecurityException("Only purchase team or management can cancel " + currentStatus + " requests");
                }
                break;

            default:
                throw new ValidationException("Cannot cancel request in " + currentStatus + " status");
        }

        // Validate factory access
        SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "cancel procurement request");
    }

    // ADDED: Missing close permission validation
    private static void validateClosePermission(ProcurementRequest request) {
        if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
            throw new SecurityException("Only purchase team can close requests");
        }

        if (request.getStatus() != ProcurementStatus.RECEIVED) {
            throw new ValidationException("Only received requests can be closed");
        }

        // Validate factory access
        SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "close procurement request");

        // Note: Pending returns validation should be done in service layer
        // using ReturnRequestService.canCloseProcurementRequest()
    }

    // NEW: Helper validation methods
    private static void validateAllLineItemsHaveVendors(ProcurementRequest request) {
        if (request.getLineItems() != null) {
            boolean hasUnassignedItems = request.getLineItems().stream()
                    .filter(item -> !item.getIsDeleted())
                    .anyMatch(item -> item.getAssignedVendor() == null || item.getAssignedPrice() == null);

            if (hasUnassignedItems) {
                throw new ValidationException("All line items must have vendors and prices assigned before marking as ordered");
            }
        }
    }

    private static void validateAllLineItemsCompleted(ProcurementRequest request) {
        if (request.getLineItems() != null) {
            boolean hasIncompleteItems = request.getLineItems().stream()
                    .filter(item -> !item.getIsDeleted())
                    .anyMatch(item -> item.getStatus() != LineItemStatus.RECEIVED &&
                            item.getStatus() != LineItemStatus.SHORT_CLOSED);

            if (hasIncompleteItems) {
                throw new ValidationException("All line items must be received or short closed before closing request");
            }
        }
    }

    private static void validateNoPendingReturns(ProcurementRequest request) {
        // Since we can't access Spring context from static method, we'll validate this differently
        // The actual validation should be done in the service layer before calling the validator

        // For now, this is a placeholder that documents the requirement
        // In practice, the service calling this should verify no pending returns exist
        // using the ReturnRequestService.canCloseProcurementRequest() method

        if (request.getLineItems() != null) {
            for (ProcurementLineItem lineItem : request.getLineItems()) {
                if (!lineItem.getIsDeleted() && lineItem.getHasReturns()) {
                    // This is a basic check - the service layer should do the detailed validation
                    throw new ValidationException("Request has return items. Verify all returns are approved before closing");
                }
            }
        }
    }

    private static void validateMarkForApprovalPermission(ProcurementRequest request) {
        if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
            throw new ValidationException("Only purchase team can mark requests for approval");
        }

        if (request.getStatus() != ProcurementStatus.SUBMITTED &&
                request.getStatus() != ProcurementStatus.IN_PROGRESS) {
            throw new ValidationException("Can only mark submitted or in-progress requests for approval");
        }

        if (request.getRequiresApproval()) {
            throw new ValidationException("Request already requires approval");
        }
    }

    private static void validateVendorAssignmentPermission(ProcurementRequest request) {
        if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
            throw new ValidationException("Only purchase team can assign vendors");
        }

        if (request.getStatus() != ProcurementStatus.SUBMITTED &&
                request.getStatus() != ProcurementStatus.IN_PROGRESS) {
            throw new ValidationException("Can only assign vendors to submitted or in-progress requests");
        }
    }

    /**
     * Validate status transition permissions
     */
    public static void validateStatusTransition(ProcurementRequest request, ProcurementStatus newStatus) {
        ProcurementStatus currentStatus = request.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // FIXED: Block status transitions when requiresApproval = true (except for management)
        if (request.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Cannot change status while request is pending management approval");
        }

        // Validate the transition is allowed
        validateStatusFlow(currentStatus, newStatus);

        // Validate user has permission for this transition
        switch (newStatus) {
            case SUBMITTED:
                if (!isFactoryUserAndCreator(request, currentUsername)) {
                    throw new ValidationException("Only the creator can submit draft requests");
                }
                // FIXED: Validate request has line items before submission
                if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
                    throw new ValidationException("Cannot submit request without line items");
                }
                break;

            case IN_PROGRESS:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can move requests to in-progress");
                }
                // FIXED: Validate request is assigned when moving to IN_PROGRESS
                if (request.getAssignedTo() == null) {
                    throw new ValidationException("Request must be assigned before moving to in-progress");
                }
                break;

            case ORDERED:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can mark requests as ordered");
                }
                // FIXED: Validate all line items have vendors assigned
                validateAllLineItemsHaveVendors(request);
                break;

            case DISPATCHED:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can mark requests as dispatched");
                }
                break;

            case RECEIVED:
                if (userRole != UserRole.FACTORY_USER) {
                    throw new ValidationException("Only factory users can mark requests as received");
                }
                // FIXED: Validate factory access
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "mark as received");
                break;

            case CLOSED:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can close requests");
                }
                // FIXED: Validate all line items are received or short closed
                validateAllLineItemsCompleted(request);
                // FIXED: Validate no pending returns
                validateNoPendingReturns(request);
                break;

            default:
                throw new ValidationException("Invalid status transition to " + newStatus);
        }
    }

    /**
     * Validate assignment permissions
     */
    public static void validateAssignmentPermission(ProcurementRequest request) {
        if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
            throw new ValidationException("Only purchase team and management can assign requests");
        }

        if (request.getStatus() != ProcurementStatus.SUBMITTED) {
            throw new ValidationException("Can only assign submitted requests");
        }
    }


    public static void validateAssignmentTarget(User assigneeUser) {
        if (assigneeUser == null) {
            throw new ValidationException("Assignee user is required");
        }

        // FIXED: Only PURCHASE_TEAM users can be assigned
        if (assigneeUser.getRole() != UserRole.PURCHASE_TEAM) {
            throw new ValidationException("Only purchase team members can be assigned to procurement requests");
        }

        // FIXED: Validate assignee is active
        if (!assigneeUser.getIsActive()) {
            throw new ValidationException("Cannot assign to inactive user");
        }
    }

    /**
     * Validate approval flag setting permissions
     */
    public static void validateApprovalFlagPermission(ProcurementRequest request, boolean newApprovalFlag) {
        ProcurementStatus status = request.getStatus();

        // Can only set approval flag on SUBMITTED or IN_PROGRESS requests
        if (status != ProcurementStatus.SUBMITTED && status != ProcurementStatus.IN_PROGRESS) {
            throw new ValidationException("Approval flag can only be set on submitted or in-progress requests");
        }

        // Only purchase team can set approval flag
        if (!SecurityUtil.canSetApprovalFlag()) {
            throw new ValidationException("Only purchase team can mark requests for approval");
        }

        // If setting to true, validate request is not already approved
        if (newApprovalFlag && request.getApprovedBy() != null) {
            throw new ValidationException("Request is already approved");
        }
    }

    /**
     * Validate approval permission
     */
    public static void validateApprovalPermission(ProcurementRequest request) {
        // Only management can approve
        if (!SecurityUtil.canApproveProcurementRequests()) {
            throw new ValidationException("Only management can approve procurement requests");
        }

        // Request must require approval
        if (!request.getRequiresApproval()) {
            throw new ValidationException("Request does not require approval");
        }

        // Request must not be already approved
        if (request.getApprovedBy() != null) {
            throw new ValidationException("Request is already approved");
        }
    }

    /**
     * Validate line item editing permissions
     */
    public static void validateLineItemEditPermission(ProcurementRequest request, String operation) {
        ProcurementStatus status = request.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // FIXED: Block line item operations when requiresApproval = true
        if (request.getRequiresApproval() && !SecurityUtil.isCurrentUserManagement()) {
            throw new ValidationException("Cannot edit line items while request is pending management approval");
        }

        switch (operation.toUpperCase()) {
            case "VENDOR_ASSIGNMENT":
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new ValidationException("Only purchase team can assign vendors to line items");
                }
                if (status != ProcurementStatus.SUBMITTED && status != ProcurementStatus.IN_PROGRESS) {
                    throw new ValidationException("Can only assign vendors in submitted or in-progress requests");
                }
                break;

            case "STATUS_UPDATE":
                validateLineItemStatusUpdatePermission(request, userRole);
                break;

            case "SHORT_CLOSE":
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new ValidationException("Only purchase team can short close line items");
                }
                break;

            case "RECEIVE":
                if (userRole != UserRole.FACTORY_USER) {
                    throw new ValidationException("Only factory users can receive line items");
                }
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "receive line items");
                break;

            default:
                throw new ValidationException("Unknown line item operation: " + operation);
        }
    }

    private static void validateLineItemStatusUpdatePermission(ProcurementRequest request, UserRole userRole) {
        ProcurementStatus requestStatus = request.getStatus();

        switch (requestStatus) {
            case SUBMITTED:
            case IN_PROGRESS:
            case ORDERED:
            case DISPATCHED:
                if (!SecurityUtil.isCurrentUserPurchaseTeamOrManagement()) {
                    throw new ValidationException("Only purchase team can update line item status in " + requestStatus + " requests");
                }
                break;
            case RECEIVED:
                if (userRole != UserRole.FACTORY_USER) {
                    throw new ValidationException("Only factory users can update line item status in received requests");
                }
                break;
            default:
                throw new ValidationException("Cannot update line item status in " + requestStatus + " requests");
        }
    }
    /**
     * Validate deletion permissions
     */
    public static void validateDeletionPermission(ProcurementRequest request) {
        // Only draft requests can be deleted
        if (request.getStatus() != ProcurementStatus.DRAFT) {
            throw new ValidationException("Only draft requests can be deleted");
        }

        // Only creator or admin can delete
        String currentUsername = SecurityUtil.getCurrentUsername();
        UserRole userRole = SecurityUtil.getCurrentUserRole();

        if (!request.getCreatedBy().equals(currentUsername) && userRole != UserRole.ADMIN) {
            throw new ValidationException("Only the creator or admin can delete draft requests");
        }

        // Validate factory access
        SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "delete request");
    }

    // Private helper methods
    private static void validateStatusBasedEditPermission(ProcurementRequest request, User currentUser) {
        ProcurementStatus status = request.getStatus();
        String currentUsername = currentUser.getUsername();
        UserRole userRole = currentUser.getRole();

        switch (status) {
            case DRAFT:
                // Only creator can edit draft requests
                if (!request.getCreatedBy().equals(currentUsername)) {
                    throw new ValidationException("Only the creator can edit draft requests");
                }
                break;

            case SUBMITTED:
            case IN_PROGRESS:
            case ORDERED:
                // Only purchase team and management can edit
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can edit requests in " + status + " status");
                }
                break;

            case DISPATCHED:
                // Limited editing allowed for purchase team
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can edit dispatched requests");
                }
                break;

            case RECEIVED:
                // Only factory users can update received requests (for confirmation)
                if (userRole != UserRole.FACTORY_USER) {
                    throw new ValidationException("Only factory users can update received requests");
                }
                // Validate factory access
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "update received request");
                break;

            case CLOSED:
                throw new ValidationException("Closed requests cannot be modified");
        }
    }

    private static void validateDraftFieldEdit(ProcurementRequest request, String fieldName, String currentUsername) {
        // Factory user (creator) can edit most fields
        if (!request.getCreatedBy().equals(currentUsername)) {
            throw new ValidationException("Only the creator can edit draft requests");
        }

        // Some fields are restricted even for creators
        List<String> restrictedFields = Arrays.asList("requestNumber", "status", "assignedTo", "approvedBy", "approvedDate");
        if (restrictedFields.contains(fieldName)) {
            throw new ValidationException("Field '" + fieldName + "' cannot be edited in draft status");
        }
    }

    private static void validateSubmittedFieldEdit(String fieldName, UserRole userRole) {
        // Only purchase team can edit, and only specific fields
        if (!isPurchaseTeamOrManagement(userRole)) {
            throw new ValidationException("Only purchase team can edit submitted requests");
        }

        // Only assignedTo field can be edited
        List<String> allowedFields = Arrays.asList("assignedTo");
        if (!allowedFields.contains(fieldName)) {
            throw new ValidationException("Only assignment can be changed in submitted requests");
        }
    }

    private static void validateInProgressFieldEdit(String fieldName, UserRole userRole) {
        // Only purchase team and management can edit
        if (!isPurchaseTeamOrManagement(userRole)) {
            throw new ValidationException("Only purchase team can edit in-progress requests");
        }

        // Allowed fields for in-progress status
        List<String> allowedFields = Arrays.asList("assignedTo", "requiresApproval", "status");
        if (!allowedFields.contains(fieldName)) {
            throw new ValidationException("Field '" + fieldName + "' cannot be edited in in-progress status");
        }
    }

    private static void validateOrderedDispatchedFieldEdit(String fieldName, UserRole userRole) {
        // Only purchase team and management can edit
        if (!isPurchaseTeamOrManagement(userRole)) {
            throw new ValidationException("Only purchase team can edit " + fieldName + " requests");
        }

        // Only status can be updated
        List<String> allowedFields = Arrays.asList("status");
        if (!allowedFields.contains(fieldName)) {
            throw new ValidationException("Only status can be updated in ordered/dispatched requests");
        }
    }

    private static void validateReceivedFieldEdit(String fieldName, UserRole userRole) {
        // Only factory users can edit received requests
        if (userRole != UserRole.FACTORY_USER) {
            throw new ValidationException("Only factory users can edit received requests");
        }

        // Only status can be updated (to closed)
        List<String> allowedFields = Arrays.asList("status");
        if (!allowedFields.contains(fieldName)) {
            throw new ValidationException("Only status can be updated in received requests");
        }
    }

    private static void validateStatusFlow(ProcurementStatus currentStatus, ProcurementStatus newStatus) {
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

    private static boolean isFactoryUserAndCreator(ProcurementRequest request, String currentUsername) {
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        return userRole == UserRole.FACTORY_USER &&
                request.getCreatedBy().equals(currentUsername);
    }

    private static boolean isPurchaseTeamOrManagement(UserRole userRole) {
        return userRole == UserRole.PURCHASE_TEAM || userRole == UserRole.MANAGEMENT || userRole == UserRole.ADMIN;
    }

    /**
     * CRITICAL: Validate factory access for all operations
     */
    public static void validateFactoryAccessForOperation(ProcurementRequest request, String operation) {
        Long factoryId = request.getFactory().getId();
        SecurityUtil.validateFactoryAccess(factoryId, operation);
    }

    private static void validateDeletePermission(ProcurementRequest request) {
        UserRole currentUserRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();
        ProcurementStatus currentStatus = request.getStatus();

        // Only draft requests can be deleted
        if (currentStatus != ProcurementStatus.DRAFT) {
            throw new ValidationException("Only draft procurement requests can be deleted");
        }

        // Only factory user (creator) can delete draft requests
        if (currentUserRole != UserRole.FACTORY_USER) {
            throw new SecurityException("Only factory users can delete procurement requests");
        }

        if (!request.getCreatedBy().equals(currentUsername)) {
            throw new SecurityException("Only the creator can delete draft requests");
        }

        // Validate factory access
        SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "delete procurement request");

        // Check if any line items have been processed
        if (request.getLineItems() != null) {
            boolean hasProcessedLineItems = request.getLineItems().stream()
                    .filter(item -> !item.getIsDeleted())
                    .anyMatch(item -> item.getAssignedVendor() != null ||
                            item.getAssignedPrice() != null ||
                            item.getStatus() != LineItemStatus.PENDING);

            if (hasProcessedLineItems) {
                throw new ValidationException("Cannot delete request. Some line items have been processed");
            }
        }
    }
}