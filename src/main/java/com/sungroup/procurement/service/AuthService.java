package com.sungroup.procurement.service;

import com.sungroup.procurement.config.JwtUtil;
import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.auth.LoginRequest;
import com.sungroup.procurement.dto.auth.LoginResponse;
import com.sungroup.procurement.dto.auth.RefreshTokenRequest;
import com.sungroup.procurement.dto.auth.UserInfo;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public ApiResponse<LoginResponse> login(LoginRequest loginRequest) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            // Get user details
            User user = userRepository.findByUsernameAndIsDeletedFalse(loginRequest.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // Generate tokens
            String accessToken = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // Create user info
            UserInfo userInfo = createUserInfo(user);

            // Create response
            LoginResponse loginResponse = new LoginResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    1800L, // 30 minutes in seconds
                    userInfo
            );

            log.info("User logged in successfully: {}", user.getUsername());
            return ApiResponse.success("Login successful", loginResponse);

        } catch (BadCredentialsException e) {
            log.error("Invalid login attempt for username: {}", loginRequest.getUsername());
            return ApiResponse.error(ProjectConstants.INVALID_CREDENTIALS);
        } catch (Exception e) {
            log.error("Error during login for username: {}", loginRequest.getUsername(), e);
            return ApiResponse.error("Login failed: " + e.getMessage());
        }
    }

    public ApiResponse<LoginResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        try {
            String refreshToken = refreshTokenRequest.getRefreshToken();

            // Validate refresh token
            if (!jwtUtil.validateToken(refreshToken)) {
                return ApiResponse.error("Invalid refresh token");
            }

            // Get username from token
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            // Get user
            User user = userRepository.findByUsernameAndIsDeletedFalse(username)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.USER_NOT_FOUND));

            // Generate new tokens
            String newAccessToken = jwtUtil.generateToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            // Create user info
            UserInfo userInfo = createUserInfo(user);

            // Create response
            LoginResponse loginResponse = new LoginResponse(
                    newAccessToken,
                    newRefreshToken,
                    "Bearer",
                    1800L, // 30 minutes in seconds
                    userInfo
            );

            log.info("Token refreshed successfully for user: {}", user.getUsername());
            return ApiResponse.success("Token refreshed successfully", loginResponse);

        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return ApiResponse.error("Token refresh failed: " + e.getMessage());
        }
    }

    private UserInfo createUserInfo(User user) {
        List<Long> factoryIds = null;
        if (user.getAssignedFactories() != null && !user.getAssignedFactories().isEmpty()) {
            factoryIds = user.getAssignedFactories().stream()
                    .map(Factory::getId)
                    .collect(Collectors.toList());
        }

        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                factoryIds,
                user.getIsActive()
        );
    }
}