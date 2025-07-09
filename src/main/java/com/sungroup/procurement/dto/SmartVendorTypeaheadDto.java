package com.sungroup.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartVendorTypeaheadDto {
    private Long id;
    private String name;
    private String email;
    private String contactNumber;
    private String contactPersonName;
    private String category; // "PREFERRED", "PREVIOUS", "OTHER"
    private BigDecimal lastPrice;
    private LocalDateTime lastOrderedDate;
    private Integer orderCount;
}
