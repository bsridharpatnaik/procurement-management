package com.sungroup.procurement.service;

import com.sungroup.procurement.config.DataInitializationConfig;
import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.Vendor;
import com.sungroup.procurement.entity.enums.UserRole;
import com.sungroup.procurement.repository.FactoryRepository;
import com.sungroup.procurement.repository.MaterialRepository;
import com.sungroup.procurement.repository.UserRepository;
import com.sungroup.procurement.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterizedDataInitializationService implements CommandLineRunner {

    private final DataInitializationConfig config;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final VendorRepository vendorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting data initialization in {} mode", config.getMode());

        switch (config.getMode()) {
            case NONE:
                log.info("Data initialization disabled");
                return;
            case MINIMAL:
                initializeMinimalData();
                break;
            case FULL:
                initializeFullData();
                break;
        }

        log.info("Data initialization completed in {} mode", config.getMode());
    }

    private void initializeMinimalData() {
        log.info("Initializing minimal data for production");

        if (config.isCreateFactories()) {
            initializeFactories();
        }

        // Always create admin user in minimal mode if users don't exist
        initializeProductionAdmin();
    }

    private void initializeFullData() {
        log.info("Initializing full data for development");

        if (config.isCreateFactories()) {
            initializeFactories();
        }

        if (config.isCreateUsers()) {
            initializeAllUsers();
        }

        if (config.isCreateMaterials()) {
            initializeMaterials();
        }

        if (config.isCreateVendors()) {
            initializeVendors();
        }
    }

    private void initializeFactories() {
        if (factoryRepository.count() == 0) {
            log.info("Initializing factories...");

            createFactory("THERMOCARE ROCKWOOL INDIA PVT. LTD.", ProjectConstants.THERMOCARE_CODE);
            createFactory("SUNTECH GEOTEXTILE PVT. LTD", ProjectConstants.SUNTECH_CODE);
            createFactory("NAAD INDUSTRIES PVT. LTD", ProjectConstants.NAAD_INDUSTRIES_CODE);
            createFactory("NAAD NONWOVEN PVT. LTD", ProjectConstants.NAAD_NONWOVEN_CODE);
            createFactory("GEOPOL INDUSTRIES PVT. LTD", ProjectConstants.GEOPOL_CODE);

            log.info("Created {} factories", 5);
        } else {
            log.info("Factories already exist, skipping initialization");
        }
    }

    private void initializeProductionAdmin() {
        if (userRepository.count() == 0) {
            log.info("Creating production admin user");

            // Validate required config
            if (config.getAdminPassword() == null || config.getAdminPassword().trim().isEmpty()) {
                log.error("Admin password not provided for production initialization");
                throw new IllegalStateException("Admin password required for production");
            }

            User admin = new User();
            admin.setUsername(config.getAdminUsername());
            admin.setPassword(passwordEncoder.encode(config.getAdminPassword()));
            admin.setFullName(config.getAdminFullName());
            admin.setEmail(config.getAdminEmail());
            admin.setRole(UserRole.ADMIN);
            admin.setIsActive(true);

            userRepository.save(admin);
            log.info("Production admin user created: {}", admin.getUsername());
        } else {
            log.info("Users already exist, skipping admin creation");
        }
    }

    private void initializeAllUsers() {
        if (userRepository.count() == 0) {
            log.info("Initializing all development users...");

            // Create Admin user
            createUser("admin", "admin123", "System Administrator",
                    "admin@sungroup.com", UserRole.ADMIN, null);

            // Create Management user
            createUser("amit.sir", "amit123", "Amit Sir",
                    "amit@sungroup.com", UserRole.MANAGEMENT, null);

            // Create Purchase Team users
            createUser("rakesh", "rakesh123", "Mr. Rakesh",
                    "rakesh@sungroup.com", UserRole.PURCHASE_TEAM, null);
            createUser("nisha", "nisha123", "Ms. Nisha",
                    "nisha@sungroup.com", UserRole.PURCHASE_TEAM, null);
            createUser("vartika", "vartika123", "Ms. Vartika",
                    "vartika@sungroup.com", UserRole.PURCHASE_TEAM, null);
            createUser("neha", "neha123", "Ms. Neha",
                    "neha@sungroup.com", UserRole.PURCHASE_TEAM, null);

            // Create Factory User
            Factory thermocare = factoryRepository.findByFactoryCodeAndIsDeletedFalse("TC").orElse(null);
            if (thermocare != null) {
                Set<Factory> assignedFactories = new HashSet<>();
                assignedFactories.add(thermocare);
                createUser("factory.tc", "factory123", "Thermocare Plant Manager",
                        "plant.tc@sungroup.com", UserRole.FACTORY_USER, assignedFactories);
            }

            log.info("Created {} users", 7);
        } else {
            log.info("Users already exist, skipping user initialization");
        }
    }

    private void initializeMaterials() {
        if (materialRepository.count() == 0) {
            log.info("Initializing materials...");

            createMaterial("Steel Rods", "kg", false);
            createMaterial("Cement", "bags", false);
            createMaterial("Electronic Components", "pieces", true);
            createMaterial("Non-woven Fabric", "meters", true);
            createMaterial("Industrial Chemicals", "liters", false);

            log.info("Created {} materials", 5);
        } else {
            log.info("Materials already exist, skipping initialization");
        }
    }

    private void initializeVendors() {
        if (vendorRepository.count() == 0) {
            log.info("Initializing vendors...");

            createVendor("ABC Steel Suppliers", "+91-9876543210", "contact@abcsteel.com");
            createVendor("XYZ Cement Company", "+91-9876543211", "sales@xyzcement.com");
            createVendor("Global Electronics Ltd", "+91-9876543212", "info@globalelectronics.com");
            createVendor("Textile Industries Pvt Ltd", "+91-9876543213", "orders@textileindustries.com");
            createVendor("Chemical Solutions Inc", "+91-9876543214", "support@chemicalsolutions.com");

            log.info("Created {} vendors", 5);
        } else {
            log.info("Vendors already exist, skipping initialization");
        }
    }

    // Helper methods
    private void createFactory(String name, String factoryCode) {
        Factory factory = new Factory();
        factory.setName(name);
        factory.setFactoryCode(factoryCode);
        factory.setIsActive(true);
        factoryRepository.save(factory);
    }

    private void createUser(String username, String password, String fullName,
                            String email, UserRole role, Set<Factory> assignedFactories) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setIsActive(true);
        if (assignedFactories != null) {
            user.setAssignedFactories(assignedFactories);
        }
        userRepository.save(user);
    }

    private void createMaterial(String name, String unit, boolean importFromChina) {
        Material material = new Material();
        material.setName(name);
        material.setUnit(unit);
        material.setImportFromChina(importFromChina);
        materialRepository.save(material);
    }

    private void createVendor(String name, String contactNumber, String email) {
        Vendor vendor = new Vendor();
        vendor.setName(name);
        vendor.setContactNumber(contactNumber);
        vendor.setEmail(email);
        vendorRepository.save(vendor);
    }
}