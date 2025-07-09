package com.sungroup.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorNameDto {
    private Long id;
    private String name;
    private String email;
    private String contactNumber;
    private String contactPersonName; // NEW FIELD
}