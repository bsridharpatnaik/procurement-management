package com.sungroup.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorPurchaseSummaryDto {
    private Long vendorId;
    private String vendorName;
    private String contactPersonName;
    private Long totalOrders;
    private BigDecimal totalAmount;
    private LocalDateTime lastPurchaseDate;
}