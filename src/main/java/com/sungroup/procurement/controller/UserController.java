package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
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
                    description = "Filter criteria (optional)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class)
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @PostMapping
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
}