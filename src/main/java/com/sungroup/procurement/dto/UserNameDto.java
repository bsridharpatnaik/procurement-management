package com.sungroup.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNameDto {
    private Long id;
    private String username;
    private String fullName;
    private String role;

    public UserNameDto(Long id, String username, String fullName) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
    }

    public String getDisplayName() {
        return fullName + " (" + username + ")";
    }
}