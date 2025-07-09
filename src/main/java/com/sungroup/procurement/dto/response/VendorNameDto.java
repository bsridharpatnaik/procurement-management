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
    private String contactPersonName; // NEW FIELD ADDED

    // Existing constructors for backward compatibility
    public VendorNameDto(String name) {
        this.name = name;
    }

    // Constructor without contactPersonName for backward compatibility
    public VendorNameDto(Long id, String name, String email, String contactNumber) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.contactNumber = contactNumber;
        this.contactPersonName = null; // Default to null
    }

    // Updated display method to include contact person
    public String getDisplayName() {
        StringBuilder displayName = new StringBuilder(name);

        if (contactPersonName != null && !contactPersonName.isEmpty()) {
            displayName.append(" - ").append(contactPersonName);
        }

        if (email != null && !email.isEmpty()) {
            displayName.append(" (").append(email).append(")");
        } else if (contactNumber != null && !contactNumber.isEmpty()) {
            displayName.append(" (").append(contactNumber).append(")");
        }

        return displayName.toString();
    }
}