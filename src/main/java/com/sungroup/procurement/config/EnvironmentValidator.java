package com.sungroup.procurement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentValidator {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final DataInitializationConfig config;

    @EventListener
    public void validateConfiguration(ApplicationReadyEvent event) {
        log.info("Validating data initialization configuration for profile: {}", activeProfile);

        if (isProduction() && config.getMode() == DataInitializationConfig.InitMode.FULL) {
            log.error("FULL data initialization mode detected in production environment!");
            throw new IllegalStateException("Cannot use FULL data initialization mode in production");
        }

        if (isProduction() && config.isCreateUsers() &&
                (config.getAdminPassword() == null || config.getAdminPassword().trim().isEmpty())) {
            log.error("Admin password not provided for production initialization");
            throw new IllegalStateException("Admin password must be provided for production environment");
        }

        log.info("Data initialization configuration validated successfully");
    }

    private boolean isProduction() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }
}