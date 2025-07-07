package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.request.PasswordUpdateRequest;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing users in the procurement system")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Get all users with filtering and pagination",
            description = "Retrieve users with optional filtering. Supports pagination and sorting.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter criteria (optional). Available filter keywords: " +
                            "• username - Filter by username (partial match) " +
                            "• fullName - Filter by full name (partial match) " +
                            "• email - Filter by email (partial match) " +
                            "• role - Filter by user role (ADMIN, FACTORY_USER, PURCHASE_TEAM, MANAGEMENT) " +
                            "• isActive - Filter by active status (true/false) " +
                            "• factoryId - Filter by assigned factory ID " +
                            "• factoryName - Filter by factory name (partial match) " +
                            "• createdBy - Filter by creator username " +
                            "• startDate - Filter by creation start date (yyyy-MM-dd HH:mm:ss) " +
                            "• endDate - Filter by creation end date (yyyy-MM-dd HH:mm:ss)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class),
                            examples = @ExampleObject(
                                    name = "Filter by role and active status",
                                    value = "{\n" +
                                            "  \"filterData\": [\n" +
                                            "    {\"attrName\": \"role\", \"attrValue\": [\"PURCHASE_TEAM\"]},\n" +
                                            "    {\"attrName\": \"isActive\", \"attrValue\": [\"true\"]}\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    )
            )
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(
            @RequestBody(required = false) FilterDataList filterDataList,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Fetching users with pagination: {}", pageable);
        ApiResponse<List<User>> response = userService.findUsersWithFilters(filterDataList, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get user by ID",
            description = "Retrieve a specific user by their unique identifier"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User found successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> getUserById(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching user by id: {}", id);
        ApiResponse<User> response = userService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get user by username",
            description = "Retrieve a specific user by their username"
    )
    @GetMapping("/username/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> getUserByUsername(
            @Parameter(description = "Username", required = true, example = "john.doe")
            @PathVariable String username) {

        log.info("Fetching user by username: {}", username);
        ApiResponse<User> response = userService.findByUsername(username);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create new user",
            description = "Create a new user in the system",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = User.class)
                    )
            )
    )
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> createUser(@Valid @RequestBody User user) {
        log.info("Creating new user: {}", user.getUsername());
        ApiResponse<User> response = userService.createUser(user);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update user",
            description = "Update an existing user's details"
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody User userDetails) {

        log.info("Updating user with id: {}", id);
        ApiResponse<User> response = userService.updateUser(id, userDetails);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Delete user",
            description = "Soft delete a user from the system"
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting user with id: {}", id);
        ApiResponse<String> response = userService.deleteUser(id);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update user password",
            description = "Allow logged-in user to update their password"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Password updated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid password or validation error"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> updatePassword(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody PasswordUpdateRequest request) {

        log.info("Password update request for user id: {}", id);
        ApiResponse<String> response = userService.updatePassword(id, request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update own password",
            description = "Allow currently logged-in user to update their own password"
    )
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<String>> updateOwnPassword(
            @Valid @RequestBody PasswordUpdateRequest request,
            Authentication authentication) {

        // Get current user from authentication context
        String currentUsername = authentication.getName();

        // Find user by username
        ApiResponse<User> userResponse = userService.findByUsername(currentUsername);
        if (!userResponse.isSuccess()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("User not found"));
        }

        Long userId = userResponse.getData().getId();
        log.info("Password update request for current user: {}", currentUsername);

        ApiResponse<String> response = userService.updatePassword(userId, request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}