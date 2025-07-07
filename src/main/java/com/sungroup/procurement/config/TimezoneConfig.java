package com.sungroup.procurement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    private static final String IST_ZONE = "Asia/Kolkata";

    @PostConstruct
    public void init() {
        // Set default timezone for the application
        TimeZone.setDefault(TimeZone.getTimeZone(IST_ZONE));
        System.setProperty("user.timezone", IST_ZONE);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setTimeZone(TimeZone.getTimeZone(IST_ZONE));
        return mapper;
    }
}