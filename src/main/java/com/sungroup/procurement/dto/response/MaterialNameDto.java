package com.sungroup.procurement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialNameDto {
    private Long id;
    private String name;
    private String unit;

    // Constructor for name only
    public MaterialNameDto(String name) {
        this.name = name;
    }

    // For display in frontend
    public String getDisplayName() {
        return unit != null ? name + " (" + unit + ")" : name;
    }
}