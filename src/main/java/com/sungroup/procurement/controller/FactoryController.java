package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.service.FactoryService;
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
@RequestMapping(ProjectConstants.API_BASE_PATH + "/factories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Factory Management", description = "APIs for managing factories in the procurement system")
public class FactoryController {

    private final FactoryService factoryService;

    @Operation(
            summary = "Get all factories with filtering and pagination",
            description = "Retrieve factories with optional filtering. Supports pagination and sorting.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter criteria (optional)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class)
                    )
            )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<List<Factory>>> getAllFactories(
            @RequestBody(required = false) FilterDataList filterDataList,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Fetching factories with pagination: {}", pageable);
        ApiResponse<List<Factory>> response = factoryService.findFactoriesWithFilters(filterDataList, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get factory by ID",
            description = "Retrieve a specific factory by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Factory>> getFactoryById(
            @Parameter(description = "Factory ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching factory by id: {}", id);
        ApiResponse<Factory> response = factoryService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get factory by factory code",
            description = "Retrieve a specific factory by its factory code"
    )
    @GetMapping("/code/{factoryCode}")
    public ResponseEntity<ApiResponse<Factory>> getFactoryByCode(
            @Parameter(description = "Factory Code", required = true, example = "TC")
            @PathVariable String factoryCode) {

        log.info("Fetching factory by code: {}", factoryCode);
        ApiResponse<Factory> response = factoryService.findByFactoryCode(factoryCode);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get active factories",
            description = "Retrieve all active factories"
    )
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Factory>>> getActiveFactories() {
        log.info("Fetching active factories");
        ApiResponse<List<Factory>> response = factoryService.findActiveFactories();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create new factory",
            description = "Create a new factory in the system",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Factory details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Factory.class)
                    )
            )
    )
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Factory>> createFactory(@Valid @RequestBody Factory factory) {
        log.info("Creating new factory: {}", factory.getName());
        ApiResponse<Factory> response = factoryService.createFactory(factory);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update factory",
            description = "Update an existing factory's details"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Factory>> updateFactory(
            @Parameter(description = "Factory ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody Factory factoryDetails) {

        log.info("Updating factory with id: {}", id);
        ApiResponse<Factory> response = factoryService.updateFactory(id, factoryDetails);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Delete factory",
            description = "Soft delete a factory from the system"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteFactory(
            @Parameter(description = "Factory ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting factory with id: {}", id);
        ApiResponse<String> response = factoryService.deleteFactory(id);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}