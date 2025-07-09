package com.sungroup.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseHistoryDto {
    private Long id;
    private String materialName;
    private String vendorName;
    private String factoryName;
    private String contactPersonName;
    private LocalDateTime purchaseDate;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String orderedBy;
    private String requestNumber;
}