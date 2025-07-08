package com.sungroup.procurement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.data-init")
@Getter
@Setter
public class DataInitializationConfig {

    public enum InitMode {
        MINIMAL,    // Only essential data
        FULL,       // All test data
        NONE        // No initialization
    }

    private InitMode mode = InitMode.MINIMAL;
    private boolean createFactories = true;
    private boolean createUsers = false;
    private boolean createMaterials = false;
    private boolean createVendors = false;
    private boolean createSampleRequests = false;

    // Production admin user config
    private String adminUsername = "admin";
    private String adminPassword;
    private String adminEmail = "admin@sungroup.com";
    private String adminFullName = "System Administrator";
}