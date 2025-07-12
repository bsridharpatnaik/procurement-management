package com.sungroup.procurement.constants;

public class ProjectConstants {

    // Date Format
    public static final String dateFormat = "yyyy-MM-dd HH:mm:ss";

    // API Base Paths
    public static final String API_BASE_PATH = "/api/v1";

    // JWT Constants
    public static final String JWT_TOKEN_PREFIX = "Bearer ";
    public static final String JWT_HEADER_STRING = "Authorization";

    // Response Messages
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

    private ProjectConstants() {
        // Private constructor to prevent instantiation
    }
}