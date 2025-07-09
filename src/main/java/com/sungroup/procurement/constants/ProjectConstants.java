package com.sungroup.procurement.constants;

public class ProjectConstants {

    // Date Format
    public static final String dateFormat = "yyyy-MM-dd HH:mm:ss";

    // API Base Paths
    public static final String API_BASE_PATH = "/api/v1";

    // JWT Constants
    public static final String JWT_TOKEN_PREFIX = "Bearer ";
    public static final String JWT_HEADER_STRING = "Authorization";

    // Request Number Prefixes
    public static final String REQUEST_NUMBER_PREFIX = "REQ";

    // Factory Codes
    public static final String THERMOCARE_CODE = "TC";
    public static final String SUNTECH_CODE = "SG";
    public static final String NAAD_INDUSTRIES_CODE = "NI";
    public static final String NAAD_NONWOVEN_CODE = "NN";
    public static final String GEOPOL_CODE = "GP";

    // Response Messages
    public static final String SUCCESS_MESSAGE = "Operation completed successfully";
    public static final String DATA_FETCHED_SUCCESS = "Data fetched successfully";
    public static final String DATA_CREATED_SUCCESS = "Data created successfully";
    public static final String DATA_UPDATED_SUCCESS = "Data updated successfully";
    public static final String DATA_DELETED_SUCCESS = "Data deleted successfully";

    // Error Messages
    public static final String INVALID_CREDENTIALS = "Invalid username or password";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String FACTORY_NOT_FOUND = "Factory not found";
    public static final String MATERIAL_NOT_FOUND = "Material not found";
    public static final String VENDOR_NOT_FOUND = "Vendor not found";
    public static final String PROCUREMENT_REQUEST_NOT_FOUND = "Procurement request not found";
    public static final String UNAUTHORIZED_ACCESS = "Unauthorized access";
    public static final String DUPLICATE_ENTRY = "Duplicate entry found";

    // Validation Messages
    public static final String REQUIRED_FIELD = "This field is required";
    public static final String INVALID_EMAIL = "Invalid email format";
    public static final String INVALID_DATE_FORMAT = "Invalid date format";
    public static final String INVALID_QUANTITY = "Quantity must be greater than zero";
    public static final String INVALID_PRICE = "Price must be greater than zero";

    public static final String REQUEST_CANCELLED_SUCCESS = "Request cancelled successfully";
    public static final String LINE_ITEM_REMOVED_SUCCESS = "Line item removed successfully";
    public static final String CANCELLATION_REASON_REQUIRED = "Cancellation reason is required";
    public static final String REMOVAL_REASON_REQUIRED = "Line item removal reason is required";
    public static final String CANNOT_CANCEL_DISPATCHED = "Cannot cancel dispatched requests";
    public static final String CANNOT_REMOVE_PROCESSED_ITEM = "Cannot remove line item with vendor assignment";
    public static final String ALREADY_CANCELLED = "Request is already cancelled";
    public static final String REQUEST_SUBMITTED_DIRECTLY = "Request created and submitted successfully";
    public static final String VENDOR_ASSIGNED_SUCCESS = "Vendor assigned successfully";

    private ProjectConstants() {
        // Private constructor to prevent instantiation
    }
}