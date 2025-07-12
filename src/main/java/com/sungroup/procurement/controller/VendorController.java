package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.SmartVendorTypeaheadDto;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.VendorNameDto;
import com.sungroup.procurement.entity.Vendor;
import com.sungroup.procurement.service.VendorService;
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
@RequestMapping(ProjectConstants.API_BASE_PATH + "/vendors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vendor Management", description = "APIs for managing vendors in the procurement system")
public class VendorController {

    private final VendorService vendorService;

    @Operation(
            summary = "Get all vendors with filtering and pagination",
            description = "Retrieve vendors with optional filtering. Supports pagination and sorting.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter criteria (optional). Available filter keywords: " +
                            "• name - Filter by vendor name (partial match, supports multiple values) " +
                            "• contactNumber - Filter by contact number (partial match) " +
                            "• email - Filter by email (partial match) " +
                            "• ids - Filter by vendor IDs (exact match) " +
                            "• createdBy - Filter by creator username " +
                            "• keyword - Search across name, email, and contact number " +
                            "• startDate - Filter by creation start date (yyyy-MM-dd HH:mm:ss) " +
                            "• endDate - Filter by creation end date (yyyy-MM-dd HH:mm:ss)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class),
                            examples = @ExampleObject(
                                    name = "Search by keyword",
                                    value = "{\n" +
                                            "  \"filterData\": [\n" +
                                            "    {\"attrName\": \"keyword\", \"attrValue\": [\"ABC\"]}\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    )
            )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<List<Vendor>>> getAllVendors(
            @RequestBody(required = false) FilterDataList filterDataList,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Fetching vendors with pagination: {}", pageable);
        ApiResponse<List<Vendor>> response = vendorService.findVendorsWithFilters(filterDataList, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get vendor by ID",
            description = "Retrieve a specific vendor by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> getVendorById(
            @Parameter(description = "Vendor ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching vendor by id: {}", id);
        ApiResponse<Vendor> response = vendorService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Search vendors",
            description = "Search vendors by keyword across name, email, and contact number fields"
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Vendor>>> searchVendors(
            @Parameter(description = "Search keyword", required = true, example = "ABC")
            @RequestParam String keyword,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        log.info("Searching vendors with keyword: {}", keyword);
        ApiResponse<List<Vendor>> response = vendorService.searchVendors(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create new vendor",
            description = "Create a new vendor in the system",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Vendor details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Vendor.class)
                    )
            )
    )
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Vendor>> createVendor(@Valid @RequestBody Vendor vendor) {
        log.info("Creating new vendor: {}", vendor.getName());
        ApiResponse<Vendor> response = vendorService.createVendor(vendor);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update vendor",
            description = "Update an existing vendor's details"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> updateVendor(
            @Parameter(description = "Vendor ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody Vendor vendorDetails) {

        log.info("Updating vendor with id: {}", id);
        ApiResponse<Vendor> response = vendorService.updateVendor(id, vendorDetails);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Delete vendor",
            description = "Soft delete a vendor from the system"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteVendor(
            @Parameter(description = "Vendor ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting vendor with id: {}", id);
        ApiResponse<String> response = vendorService.deleteVendor(id);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Get vendor names for typeahead",
            description = "Retrieve all active vendor names for autocomplete/typeahead functionality"
    )
    @GetMapping("/names")
    public ResponseEntity<ApiResponse<List<String>>> getAllVendorNames(
            @Parameter(description = "Search query for filtering names", example = "ABC")
            @RequestParam(required = false) String search) {

        log.info("Fetching vendor names for typeahead with search: {}", search);
        ApiResponse<List<String>> response = vendorService.getAllVendorNames(search);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get vendor names and IDs for typeahead",
            description = "Retrieve vendor names with their IDs and contact info for autocomplete with selection"
    )
    @GetMapping("/names-with-ids")
    public ResponseEntity<ApiResponse<List<VendorNameDto>>> getVendorNamesWithIds(
            @Parameter(description = "Search query for filtering names", example = "ABC")
            @RequestParam(required = false) String search,
            @Parameter(description = "Maximum number of results", example = "50")
            @RequestParam(defaultValue = "50") Integer limit) {

        log.info("Fetching vendor names with IDs for typeahead with search: {} and limit: {}", search, limit);
        ApiResponse<List<VendorNameDto>> response = vendorService.getVendorNamesWithIds(search, limit);
        return ResponseEntity.ok(response);
    }
}