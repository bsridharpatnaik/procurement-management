package com.sungroup.procurement.util;

import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.User;
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

        // Check if request requires approval and is locked
        if (request.getRequiresApproval() && !SecurityUtil.canApproveProcurementRequests()) {
            throw new ValidationException("Request is pending approval and cannot be edited until approved");
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

    /**
     * Validate status transition permissions
     */
    public static void validateStatusTransition(ProcurementRequest request, ProcurementStatus newStatus) {
        ProcurementStatus currentStatus = request.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();
        String currentUsername = SecurityUtil.getCurrentUsername();

        // Validate the transition is allowed
        validateStatusFlow(currentStatus, newStatus);

        // Validate user has permission for this transition
        switch (newStatus) {
            case SUBMITTED:
                if (!isFactoryUserAndCreator(request, currentUsername)) {
                    throw new ValidationException("Only the creator can submit draft requests");
                }
                break;

            case IN_PROGRESS:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can move requests to in progress");
                }
                break;

            case ORDERED:
            case DISPATCHED:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can change status to " + newStatus);
                }
                break;

            case RECEIVED:
                if (userRole != UserRole.FACTORY_USER) {
                    throw new ValidationException("Only factory users can mark requests as received");
                }
                // Validate factory access
                SecurityUtil.validateFactoryAccess(request.getFactory().getId(), "mark as received");
                break;

            case CLOSED:
                if (!isPurchaseTeamOrManagement(userRole)) {
                    throw new ValidationException("Only purchase team can close requests");
                }
                break;
        }
    }

    /**
     * Validate assignment permissions
     */
    public static void validateAssignmentPermission(User assignedUser) {
        // Only purchase team and management can assign requests
        if (!SecurityUtil.canAssignProcurementRequests()) {
            throw new ValidationException("You don't have permission to assign procurement requests");
        }

        // Only purchase team users can be assigned
        if (!SecurityUtil.canBeAssignedToProcurementRequests(assignedUser)) {
            throw new ValidationException("User cannot be assigned to procurement requests. Only purchase team members can be assigned.");
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

        switch (status) {
            case DRAFT:
                // Only creator can edit line items in draft
                if (!request.getCreatedBy().equals(currentUsername)) {
                    throw new ValidationException("Only the creator can edit line items in draft requests");
                }
                break;

            case SUBMITTED:
                throw new ValidationException("Cannot edit line items in submitted requests");

            case IN_PROGRESS:
                // Only purchase team can edit vendor/price assignments
                if ("VENDOR_ASSIGNMENT".equals(operation) || "PRICE_ASSIGNMENT".equals(operation)) {
                    if (!isPurchaseTeamOrManagement(userRole)) {
                        throw new ValidationException("Only purchase team can assign vendors and prices");
                    }
                } else {
                    throw new ValidationException("Only vendor and price assignments allowed in in-progress requests");
                }
                break;

            case ORDERED:
            case DISPATCHED:
            case RECEIVED:
            case CLOSED:
                throw new ValidationException("Cannot edit line items in " + status.toString().toLowerCase() + " requests");
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
        return SecurityUtil.getCurrentUserRole() == UserRole.FACTORY_USER &&
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

    /**
     * CRITICAL: Validate user can perform specific operation based on request status
     */
    public static void validateOperationPermission(ProcurementRequest request, String operation) {
        validateFactoryAccessForOperation(request, operation);

        // Additional status-based validation
        ProcurementStatus status = request.getStatus();
        UserRole userRole = SecurityUtil.getCurrentUserRole();

        switch (operation) {
            case "VIEW":
                // Factory access already validated above
                break;
            case "EDIT":
                validateCanEditProcurementRequest(request);
                break;
            case "DELETE":
                validateDeletionPermission(request);
                break;
            case "ASSIGN_VENDOR":
                validateLineItemEditPermission(request, "VENDOR_ASSIGNMENT");
                break;
            case "APPROVE":
                validateApprovalPermission(request);
                break;
            default:
                throw new ValidationException("Unknown operation: " + operation);
        }
    }
}