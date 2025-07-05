package com.sungroup.procurement.dto.auth;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private List<Long> factoryIds;
    private boolean isActive;
}