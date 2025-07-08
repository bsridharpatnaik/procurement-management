package com.sungroup.procurement.util;

import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.ReturnRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class for filtering response data based on user roles
 * Primarily used to hide vendor information from factory users
 */
@Component
public class ResponseFilterUtil {

    /**
     * Filter vendor information from procurement requests based on user role
     */
    public static List<ProcurementRequest> filterProcurementRequestsForUser(List<ProcurementRequest> requests) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return requests; // No filtering needed for purchase team/management/admin
        }

        // Hide vendor information for factory users
        requests.forEach(ResponseFilterUtil::hideVendorInformationFromRequest);
        return requests;
    }

    /**
     * Filter vendor information from a single procurement request
     */
    public static ProcurementRequest filterProcurementRequestForUser(ProcurementRequest request) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return request; // No filtering needed
        }

        hideVendorInformationFromRequest(request);
        return request;
    }

    /**
     * Filter vendor information from line items
     */
    public static List<ProcurementLineItem> filterLineItemsForUser(List<ProcurementLineItem> lineItems) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return lineItems; // No filtering needed
        }

        lineItems.forEach(ResponseFilterUtil::hideVendorInformationFromLineItem);
        return lineItems;
    }

    /**
     * Filter vendor information from a single line item
     */
    public static ProcurementLineItem filterLineItemForUser(ProcurementLineItem lineItem) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return lineItem; // No filtering needed
        }

        hideVendorInformationFromLineItem(lineItem);
        return lineItem;
    }

    /**
     * Filter vendor information from return requests
     */
    public static List<ReturnRequest> filterReturnRequestsForUser(List<ReturnRequest> returnRequests) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return returnRequests; // No filtering needed
        }

        returnRequests.forEach(ResponseFilterUtil::hideVendorInformationFromReturnRequest);
        return returnRequests;
    }

    /**
     * Filter vendor information from a single return request
     */
    public static ReturnRequest filterReturnRequestForUser(ReturnRequest returnRequest) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return returnRequest; // No filtering needed
        }

        hideVendorInformationFromReturnRequest(returnRequest);
        return returnRequest;
    }

    // Private helper methods for hiding vendor information

    /**
     * Hide vendor information from procurement request and its line items
     */
    private static void hideVendorInformationFromRequest(ProcurementRequest request) {
        if (request.getLineItems() != null && !request.getLineItems().isEmpty()) {
            request.getLineItems().forEach(ResponseFilterUtil::hideVendorInformationFromLineItem);
        }
    }

    /**
     * Hide vendor information from a single line item
     */
    private static void hideVendorInformationFromLineItem(ProcurementLineItem lineItem) {
        // Remove vendor reference completely
        lineItem.setAssignedVendor(null);

        // Remove price information
        lineItem.setAssignedPrice(null);

        // If there are return requests, filter them too
        if (lineItem.getReturnRequests() != null && !lineItem.getReturnRequests().isEmpty()) {
            lineItem.getReturnRequests().forEach(ResponseFilterUtil::hideVendorInformationFromReturnRequest);
        }
    }

    /**
     * Hide vendor information from return request (through its line item)
     */
    private static void hideVendorInformationFromReturnRequest(ReturnRequest returnRequest) {
        if (returnRequest.getProcurementLineItem() != null) {
            hideVendorInformationFromLineItem(returnRequest.getProcurementLineItem());
        }
    }

    /**
     * Create a sanitized copy of procurement request without vendor information
     * This method creates a deep copy to avoid modifying the original entity
     */
    public static ProcurementRequest createSanitizedCopy(ProcurementRequest original) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return original; // Return original if user can see vendor info
        }

        // For factory users, we filter in place since we're already working with
        // entities that will be serialized to JSON
        return filterProcurementRequestForUser(original);
    }

    /**
     * Check if current response should include vendor information
     */
    public static boolean shouldIncludeVendorInformation() {
        return SecurityUtil.canSeeVendorInformation();
    }

    /**
     * Get a safe vendor display name for factory users
     * Returns null for factory users, vendor name for others
     */
    public static String getSafeVendorDisplayName(String vendorName) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return vendorName;
        }
        return null; // Hide vendor name from factory users
    }

    /**
     * Get safe price display for factory users
     * Returns null for factory users, actual price for others
     */
    public static java.math.BigDecimal getSafePriceDisplay(java.math.BigDecimal price) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return price;
        }
        return null; // Hide price from factory users
    }

    /**
     * Create response message that's appropriate for user role
     */
    public static String createRoleBasedMessage(String messageForPurchaseTeam, String messageForFactoryUser) {
        if (SecurityUtil.canSeeVendorInformation()) {
            return messageForPurchaseTeam;
        }
        return messageForFactoryUser;
    }

    /**
     * Filter any object based on user role - generic method
     */
    @SuppressWarnings("unchecked")
    public static <T> T filterObjectForUser(T object) {
        if (object == null) {
            return null;
        }

        if (object instanceof ProcurementRequest) {
            return (T) filterProcurementRequestForUser((ProcurementRequest) object);
        } else if (object instanceof ProcurementLineItem) {
            return (T) filterLineItemForUser((ProcurementLineItem) object);
        } else if (object instanceof ReturnRequest) {
            return (T) filterReturnRequestForUser((ReturnRequest) object);
        } else if (object instanceof List) {
            List<?> list = (List<?>) object;
            if (!list.isEmpty()) {
                Object firstItem = list.get(0);
                if (firstItem instanceof ProcurementRequest) {
                    return (T) filterProcurementRequestsForUser((List<ProcurementRequest>) list);
                } else if (firstItem instanceof ProcurementLineItem) {
                    return (T) filterLineItemsForUser((List<ProcurementLineItem>) list);
                } else if (firstItem instanceof ReturnRequest) {
                    return (T) filterReturnRequestsForUser((List<ReturnRequest>) list);
                }
            }
        }

        // If no specific filtering needed, return as is
        return object;
    }
}