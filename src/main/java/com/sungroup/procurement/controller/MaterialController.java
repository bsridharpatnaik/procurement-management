package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.MaterialNameDto;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.service.MaterialService;
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
@RequestMapping(ProjectConstants.API_BASE_PATH + "/materials")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Material Management", description = "APIs for managing materials in the procurement system")
public class MaterialController {

    private final MaterialService materialService;

    @Operation(
            summary = "Get all materials with filtering and pagination",
            description = "Retrieve materials with optional filtering. Supports pagination and sorting.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter criteria (optional). Available filter keywords: " +
                            "• name - Filter by material name (partial match, supports multiple values) " +
                            "• unit - Filter by unit (exact match) " +
                            "• units - Filter by multiple units (exact match) " +
                            "• importFromChina - Filter by import flag (true/false) " +
                            "• ids - Filter by material IDs (exact match) " +
                            "• createdBy - Filter by creator username " +
                            "• keyword - Search across name and unit fields " +
                            "• startDate - Filter by creation start date (yyyy-MM-dd HH:mm:ss) " +
                            "• endDate - Filter by creation end date (yyyy-MM-dd HH:mm:ss)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class),
                            examples = @ExampleObject(
                                    name = "Filter by import flag and units",
                                    value = "{\n" +
                                            "  \"filterData\": [\n" +
                                            "    {\"attrName\": \"importFromChina\", \"attrValue\": [\"true\"]},\n" +
                                            "    {\"attrName\": \"units\", \"attrValue\": [\"kg\", \"pieces\"]}\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    )
            )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<List<Material>>> getAllMaterials(
            @RequestBody(required = false) FilterDataList filterDataList,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Fetching materials with pagination: {}", pageable);
        ApiResponse<List<Material>> response = materialService.findMaterialsWithFilters(filterDataList, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get material by ID",
            description = "Retrieve a specific material by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Material>> getMaterialById(
            @Parameter(description = "Material ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching material by id: {}", id);
        ApiResponse<Material> response = materialService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get material by name",
            description = "Retrieve a specific material by its name"
    )
    @GetMapping("/name/{name}")
    public ResponseEntity<ApiResponse<Material>> getMaterialByName(
            @Parameter(description = "Material Name", required = true, example = "Steel Rods")
            @PathVariable String name) {

        log.info("Fetching material by name: {}", name);
        ApiResponse<Material> response = materialService.findByName(name);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Search materials",
            description = "Search materials by keyword across name and unit fields"
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Material>>> searchMaterials(
            @Parameter(description = "Search keyword", required = true, example = "steel")
            @RequestParam String keyword,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        log.info("Searching materials with keyword: {}", keyword);
        ApiResponse<List<Material>> response = materialService.searchMaterials(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get all material units",
            description = "Retrieve all distinct units used by materials"
    )
    @GetMapping("/units")
    public ResponseEntity<ApiResponse<List<String>>> getAllUnits() {
        log.info("Fetching all material units");
        ApiResponse<List<String>> response = materialService.getAllUnits();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get materials by import flag",
            description = "Retrieve materials based on whether they are imported from China"
    )
    @GetMapping("/import-from-china")
    public ResponseEntity<ApiResponse<List<Material>>> getMaterialsByImportFlag(
            @Parameter(description = "Import from China flag", required = true, example = "true")
            @RequestParam Boolean importFromChina) {

        log.info("Fetching materials by import flag: {}", importFromChina);
        ApiResponse<List<Material>> response = materialService.findByImportFromChina(importFromChina);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create new material",
            description = "Create a new material in the system",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Material details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Material.class)
                    )
            )
    )
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Material>> createMaterial(@Valid @RequestBody Material material) {
        log.info("Creating new material: {}", material.getName());
        ApiResponse<Material> response = materialService.createMaterial(material);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update material",
            description = "Update an existing material's details"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Material>> updateMaterial(
            @Parameter(description = "Material ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody Material materialDetails) {

        log.info("Updating material with id: {}", id);
        ApiResponse<Material> response = materialService.updateMaterial(id, materialDetails);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Delete material",
            description = "Soft delete a material from the system"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteMaterial(
            @Parameter(description = "Material ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting material with id: {}", id);
        ApiResponse<String> response = materialService.deleteMaterial(id);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Get material names for typeahead",
            description = "Retrieve all active material names for autocomplete/typeahead functionality"
    )
    @GetMapping("/names")
    public ResponseEntity<ApiResponse<List<String>>> getAllMaterialNames(
            @Parameter(description = "Search query for filtering names", example = "steel")
            @RequestParam(required = false) String search) {

        log.info("Fetching material names for typeahead with search: {}", search);
        ApiResponse<List<String>> response = materialService.getAllMaterialNames(search);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get material names and IDs for typeahead",
            description = "Retrieve material names with their IDs for autocomplete with selection"
    )
    @GetMapping("/names-with-ids")
    public ResponseEntity<ApiResponse<List<MaterialNameDto>>> getMaterialNamesWithIds(
            @Parameter(description = "Search query for filtering names", example = "steel")
            @RequestParam(required = false) String search,
            @Parameter(description = "Maximum number of results", example = "50")
            @RequestParam(defaultValue = "50") Integer limit) {

        log.info("Fetching material names with IDs for typeahead with search: {} and limit: {}", search, limit);
        ApiResponse<List<MaterialNameDto>> response = materialService.getMaterialNamesWithIds(search, limit);
        return ResponseEntity.ok(response);
    }

}