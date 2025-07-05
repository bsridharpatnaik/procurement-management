package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.auth.LoginRequest;
import com.sungroup.procurement.dto.auth.LoginResponse;
import com.sungroup.procurement.dto.auth.RefreshTokenRequest;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "APIs for user authentication and authorization")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "User login",
            description = "Authenticate user with username and password. Returns JWT tokens for authorization.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Login credentials",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for username: {}", loginRequest.getUsername());

        ApiResponse<LoginResponse> response = authService.login(loginRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Refresh JWT token",
            description = "Generate new access token using refresh token",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refresh token",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshTokenRequest.class)
                    )
            )
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        log.info("Token refresh attempt");

        ApiResponse<LoginResponse> response = authService.refreshToken(refreshTokenRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Logout user",
            description = "Logout user (client should remove tokens)"
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        // In JWT, logout is typically handled on the client side by removing tokens
        // For server-side logout, you'd need to maintain a blacklist of tokens

        log.info("Logout request received");
        return ResponseEntity.ok(ApiResponse.success("Logout successful", "User logged out successfully"));
    }
}