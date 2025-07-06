package com.sungroup.procurement.dto.response;

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

    // Constructor for name only
    public VendorNameDto(String name) {
        this.name = name;
    }

    // For display in frontend with contact info
    public String getDisplayName() {
        if (email != null && !email.isEmpty()) {
            return name + " (" + email + ")";
        } else if (contactNumber != null && !contactNumber.isEmpty()) {
            return name + " (" + contactNumber + ")";
        }
        return name;
    }
}