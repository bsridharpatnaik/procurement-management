package com.sungroup.procurement.service;

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
public class DataInitializationService implements CommandLineRunner {

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final VendorRepository vendorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting data initialization...");

        initializeFactories();
        initializeUsers();
        initializeMaterials();
        initializeVendors();

        log.info("Data initialization completed!");
    }

    private void initializeFactories() {
        if (factoryRepository.count() == 0) {
            log.info("Initializing factories...");

            Factory factory1 = new Factory();
            factory1.setName("THERMOCARE ROCKWOOL INDIA PVT. LTD.");
            factory1.setFactoryCode(ProjectConstants.THERMOCARE_CODE);
            factory1.setIsActive(true);
            factoryRepository.save(factory1);

            Factory factory2 = new Factory();
            factory2.setName("SUNTECH GEOTEXTILE PVT. LTD");
            factory2.setFactoryCode(ProjectConstants.SUNTECH_CODE);
            factory2.setIsActive(true);
            factoryRepository.save(factory2);

            Factory factory3 = new Factory();
            factory3.setName("NAAD INDUSTRIES PVT. LTD");
            factory3.setFactoryCode(ProjectConstants.NAAD_INDUSTRIES_CODE);
            factory3.setIsActive(true);
            factoryRepository.save(factory3);

            Factory factory4 = new Factory();
            factory4.setName("NAAD NONWOVEN PVT. LTD");
            factory4.setFactoryCode(ProjectConstants.NAAD_NONWOVEN_CODE);
            factory4.setIsActive(true);
            factoryRepository.save(factory4);

            Factory factory5 = new Factory();
            factory5.setName("GEOPOL INDUSTRIES PVT. LTD");
            factory5.setFactoryCode(ProjectConstants.GEOPOL_CODE);
            factory5.setIsActive(true);
            factoryRepository.save(factory5);

            log.info("Created {} factories", 5);
        }
    }

    private void initializeUsers() {
        if (userRepository.count() == 0) {
            log.info("Initializing users...");

            // Create Admin user
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("System Administrator");
            admin.setEmail("admin@sungroup.com");
            admin.setRole(UserRole.ADMIN);
            admin.setIsActive(true);
            userRepository.save(admin);

            // Create Management user
            User management = new User();
            management.setUsername("amit.sir");
            management.setPassword(passwordEncoder.encode("amit123"));
            management.setFullName("Amit Sir");
            management.setEmail("amit@sungroup.com");
            management.setRole(UserRole.MANAGEMENT);
            management.setIsActive(true);
            userRepository.save(management);

            // Create Purchase Team users
            User purchaseManager = new User();
            purchaseManager.setUsername("rakesh");
            purchaseManager.setPassword(passwordEncoder.encode("rakesh123"));
            purchaseManager.setFullName("Mr. Rakesh");
            purchaseManager.setEmail("rakesh@sungroup.com");
            purchaseManager.setRole(UserRole.PURCHASE_TEAM);
            purchaseManager.setIsActive(true);
            userRepository.save(purchaseManager);

            User nisha = new User();
            nisha.setUsername("nisha");
            nisha.setPassword(passwordEncoder.encode("nisha123"));
            nisha.setFullName("Ms. Nisha");
            nisha.setEmail("nisha@sungroup.com");
            nisha.setRole(UserRole.PURCHASE_TEAM);
            nisha.setIsActive(true);
            userRepository.save(nisha);

            User vartika = new User();
            vartika.setUsername("vartika");
            vartika.setPassword(passwordEncoder.encode("vartika123"));
            vartika.setFullName("Ms. Vartika");
            vartika.setEmail("vartika@sungroup.com");
            vartika.setRole(UserRole.PURCHASE_TEAM);
            vartika.setIsActive(true);
            userRepository.save(vartika);

            User neha = new User();
            neha.setUsername("neha");
            neha.setPassword(passwordEncoder.encode("neha123"));
            neha.setFullName("Ms. Neha");
            neha.setEmail("neha@sungroup.com");
            neha.setRole(UserRole.PURCHASE_TEAM);
            neha.setIsActive(true);
            userRepository.save(neha);

            // Create Factory User
            Factory thermocare = factoryRepository.findByFactoryCodeAndIsDeletedFalse("TC").orElse(null);
            if (thermocare != null) {
                User factoryUser = new User();
                factoryUser.setUsername("factory.tc");
                factoryUser.setPassword(passwordEncoder.encode("factory123"));
                factoryUser.setFullName("Thermocare Plant Manager");
                factoryUser.setEmail("plant.tc@sungroup.com");
                factoryUser.setRole(UserRole.FACTORY_USER);
                factoryUser.setIsActive(true);
                Set<Factory> assignedFactories = new HashSet<>();
                assignedFactories.add(thermocare);
                factoryUser.setAssignedFactories(assignedFactories);
                userRepository.save(factoryUser);
            }

            log.info("Created {} users", 7);
        }
    }

    private void initializeMaterials() {
        if (materialRepository.count() == 0) {
            log.info("Initializing materials...");

            Material steel = new Material();
            steel.setName("Steel Rods");
            steel.setUnit("kg");
            steel.setImportFromChina(false);
            materialRepository.save(steel);

            Material cement = new Material();
            cement.setName("Cement");
            cement.setUnit("bags");
            cement.setImportFromChina(false);
            materialRepository.save(cement);

            Material electronics = new Material();
            electronics.setName("Electronic Components");
            electronics.setUnit("pieces");
            electronics.setImportFromChina(true);
            materialRepository.save(electronics);

            Material fabric = new Material();
            fabric.setName("Non-woven Fabric");
            fabric.setUnit("meters");
            fabric.setImportFromChina(true);
            materialRepository.save(fabric);

            Material chemicals = new Material();
            chemicals.setName("Industrial Chemicals");
            chemicals.setUnit("liters");
            chemicals.setImportFromChina(false);
            materialRepository.save(chemicals);

            log.info("Created {} materials", 5);
        }
    }

    private void initializeVendors() {
        if (vendorRepository.count() == 0) {
            log.info("Initializing vendors...");

            Vendor vendor1 = new Vendor();
            vendor1.setName("ABC Steel Suppliers");
            vendor1.setContactNumber("+91-9876543210");
            vendor1.setEmail("contact@abcsteel.com");
            vendorRepository.save(vendor1);

            Vendor vendor2 = new Vendor();
            vendor2.setName("XYZ Cement Company");
            vendor2.setContactNumber("+91-9876543211");
            vendor2.setEmail("sales@xyzcement.com");
            vendorRepository.save(vendor2);

            Vendor vendor3 = new Vendor();
            vendor3.setName("Global Electronics Ltd");
            vendor3.setContactNumber("+91-9876543212");
            vendor3.setEmail("info@globalelectronics.com");
            vendorRepository.save(vendor3);

            Vendor vendor4 = new Vendor();
            vendor4.setName("Textile Industries Pvt Ltd");
            vendor4.setContactNumber("+91-9876543213");
            vendor4.setEmail("orders@textileindustries.com");
            vendorRepository.save(vendor4);

            Vendor vendor5 = new Vendor();
            vendor5.setName("Chemical Solutions Inc");
            vendor5.setContactNumber("+91-9876543214");
            vendor5.setEmail("support@chemicalsolutions.com");
            vendorRepository.save(vendor5);

            log.info("Created {} vendors", 5);
        }
    }
}