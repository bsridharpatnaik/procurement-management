package com.sungroup.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
@EnableConfigurationProperties
public class ProcurementManagementApplication extends SpringBootServletInitializer {


    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ProcurementManagementApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(ProcurementManagementApplication.class, args);
    }
}