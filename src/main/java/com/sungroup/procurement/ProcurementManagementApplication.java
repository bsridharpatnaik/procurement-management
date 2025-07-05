package com.sungroup.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class ProcurementManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcurementManagementApplication.class, args);
    }
}