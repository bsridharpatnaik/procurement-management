package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.MaterialNameDto;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.MaterialPriceHistoryRepository;
import com.sungroup.procurement.repository.MaterialRepository;
import com.sungroup.procurement.repository.ProcurementLineItemRepository;
import com.sungroup.procurement.specification.MaterialSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final FilterService filterService;

    private final ProcurementLineItemRepository procurementLineItemRepository;
    private final MaterialPriceHistoryRepository priceHistoryRepository;

    // READ Operations
    public ApiResponse<List<Material>> findMaterialsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<Material> spec = buildMaterialSpecification(filterData);
            Page<Material> materialPage = materialRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(materialPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materialPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching materials with filters", e);
            return ApiResponse.error("Failed to fetch materials: " + e.getMessage());
        }
    }

    public ApiResponse<Material> findById(Long id) {
        try {
            Material material = materialRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, material);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching material by id: {}", id, e);
            return ApiResponse.error("Failed to fetch material");
        }
    }

    public ApiResponse<Material> findByName(String name) {
        try {
            Material material = materialRepository.findByNameAndIsDeletedFalse(name)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, material);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching material by name: {}", name, e);
            return ApiResponse.error("Failed to fetch material");
        }
    }

    public ApiResponse<List<String>> getAllUnits() {
        try {
            List<String> units = materialRepository.findAllDistinctUnits();
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, units);
        } catch (Exception e) {
            log.error("Error fetching material units", e);
            return ApiResponse.error("Failed to fetch material units");
        }
    }

    public ApiResponse<List<Material>> findByImportFromChina(Boolean importFromChina) {
        try {
            Specification<Material> spec = MaterialSpecification.isNotDeleted()
                    .and(MaterialSpecification.isImportFromChina(importFromChina));

            List<Material> materials = materialRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materials);
        } catch (Exception e) {
            log.error("Error fetching materials by import flag", e);
            return ApiResponse.error("Failed to fetch materials");
        }
    }

    public ApiResponse<List<Material>> searchMaterials(String keyword, Pageable pageable) {
        try {
            Specification<Material> spec = MaterialSpecification.isNotDeleted()
                    .and(MaterialSpecification.searchByKeyword(keyword));

            Page<Material> materialPage = materialRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(materialPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materialPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error searching materials with keyword: {}", keyword, e);
            return ApiResponse.error("Failed to search materials");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<Material> createMaterial(Material material) {
        try {
            preprocessMaterial(material);
            validateMaterialForCreate(material);
            Material savedMaterial = materialRepository.save(material);
            log.info("Material created successfully: {}", savedMaterial.getName());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedMaterial);
        } catch (DuplicateEntityException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating material", e);
            return ApiResponse.error("Failed to create material");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<Material> updateMaterial(Long id, Material materialDetails) {
        try {
            Material existingMaterial = materialRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            preprocessMaterial(materialDetails);
            validateMaterialForUpdate(materialDetails, existingMaterial);

            // Update fields
            if (materialDetails.getName() != null) {
                existingMaterial.setName(materialDetails.getName());
            }
            if (materialDetails.getUnit() != null) {
                existingMaterial.setUnit(materialDetails.getUnit());
            }
            if (materialDetails.getImportFromChina() != null) {
                existingMaterial.setImportFromChina(materialDetails.getImportFromChina());
            }

            Material updatedMaterial = materialRepository.save(existingMaterial);
            log.info("Material updated successfully: {}", updatedMaterial.getName());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedMaterial);
        } catch (EntityNotFoundException | ValidationException | DuplicateEntityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating material with id: {}", id, e);
            return ApiResponse.error("Failed to update material");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteMaterial(Long id) {
        try {
            Material material = materialRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            List<String> dependencies = new ArrayList<>();

            // Check procurement line items
            long lineItemCount = procurementLineItemRepository.countByMaterialIdAndProcurementRequestIsDeletedFalse(id);
            if (lineItemCount > 0) {
                dependencies.add("Procurement Line Items: " + lineItemCount + " items");
            }

            // Check price history
            long priceHistoryCount = priceHistoryRepository.countByMaterialId(id);
            if (priceHistoryCount > 0) {
                dependencies.add("Price History: " + priceHistoryCount + " records");
            }

            if (!dependencies.isEmpty()) {
                String message = "Cannot delete material '" + material.getName() +
                        "'. It is being used in: " + String.join("; ", dependencies);
                return ApiResponse.error(message);
            }

            // Soft delete if no dependencies
            material.setIsDeleted(true);
            materialRepository.save(material);

            log.info("Material soft deleted successfully: {}", material.getName());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS,
                    "Material '" + material.getName() + "' deleted successfully");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting material with id: {}", id, e);
            return ApiResponse.error("Failed to delete material");
        }
    }

    // UTILITY Methods
    private Specification<Material> buildMaterialSpecification(FilterDataList filterData) {
        Specification<Material> spec = MaterialSpecification.isNotDeleted();

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        // Get multiple names from filter
        List<String> names = filterService.getStringValues(filterData, "name");
        String unit = filterService.getStringValue(filterData, "unit");
        List<String> units = filterService.getStringValues(filterData, "units");
        Boolean importFromChina = filterService.getBooleanValue(filterData, "importFromChina");
        List<Long> ids = filterService.getLongValues(filterData, "ids");
        String createdBy = filterService.getStringValue(filterData, "createdBy");
        String keyword = filterService.getStringValue(filterData, "keyword");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        // Enhanced name filtering - supports multiple names
        if (names != null && !names.isEmpty()) {
            if (names.size() == 1) {
                // Single name - use contains (partial match)
                spec = spec.and(MaterialSpecification.hasName(names.get(0)));
            } else {
                // Multiple names - use OR logic with partial match
                spec = spec.and(MaterialSpecification.searchByMultipleNames(names));
            }
        }

        if (unit != null) {
            spec = spec.and(MaterialSpecification.hasUnit(unit));
        }
        if (units != null && !units.isEmpty()) {
            spec = spec.and(MaterialSpecification.hasUnits(units));
        }
        if (importFromChina != null) {
            spec = spec.and(MaterialSpecification.isImportFromChina(importFromChina));
        }
        if (ids != null && !ids.isEmpty()) {
            spec = spec.and(MaterialSpecification.hasIds(ids));
        }
        if (createdBy != null) {
            spec = spec.and(MaterialSpecification.createdBy(createdBy));
        }
        if (keyword != null) {
            spec = spec.and(MaterialSpecification.searchByKeyword(keyword));
        }
        if (createdDateRange != null) {
            spec = spec.and(MaterialSpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        return spec;
    }

    private void validateMaterialForCreate(Material material) {
        if (material.getName() == null || material.getName().trim().isEmpty()) {
            throw new ValidationException("Material name is required");
        }

        // After preprocessing, name should already be trimmed, but double-check
        String trimmedName = material.getName().trim();
        if (trimmedName.isEmpty()) {
            throw new ValidationException("Material name cannot be empty or only spaces");
        }

        // CHANGE: Use case-insensitive duplicate check
        if (materialRepository.existsByNameIgnoreCaseAndIsDeletedFalse(trimmedName)) {
            throw new DuplicateEntityException("Material name already exists");
        }
    }


    private void validateMaterialForUpdate(Material materialDetails, Material existingMaterial) {
        // Validate name if provided (name is required field)
        if (materialDetails.getName() != null) {
            String trimmedName = materialDetails.getName().trim();

            if (trimmedName.isEmpty()) {
                throw new ValidationException("Material name cannot be empty");
            }

            // CHANGE: Use case-insensitive comparison and duplicate check
            if (!trimmedName.equalsIgnoreCase(existingMaterial.getName())) {
                if (materialRepository.existsByNameIgnoreCaseAndIsDeletedFalse(trimmedName)) {
                    throw new DuplicateEntityException("Material name already exists");
                }
            }
        }

        // Validate unit if provided (unit can be optional)
        if (materialDetails.getUnit() != null && materialDetails.getUnit().trim().isEmpty()) {
            throw new ValidationException("Unit cannot be empty");
        }
    }

    public ApiResponse<List<String>> getAllMaterialNames(String search) {
        try {
            List<String> materialNames;

            if (search != null && !search.trim().isEmpty()) {
                // Filtered search
                materialNames = materialRepository.findMaterialNamesByNameContainingIgnoreCase(search.trim());
            } else {
                // All material names
                materialNames = materialRepository.findAllActiveMaterialNames();
            }

            // Sort alphabetically
            materialNames.sort(String.CASE_INSENSITIVE_ORDER);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materialNames);
        } catch (Exception e) {
            log.error("Error fetching material names for typeahead with search: {}", search, e);
            return ApiResponse.error("Failed to fetch material names");
        }
    }

    public ApiResponse<List<MaterialNameDto>> getMaterialNamesWithIds(String search, Integer limit) {
        try {
            List<MaterialNameDto> materials;

            Pageable pageable = PageRequest.of(0, limit != null ? limit : 50, Sort.by("name"));

            if (search != null && !search.trim().isEmpty()) {
                // Filtered search with limit
                materials = materialRepository.findMaterialNamesWithIdsByNameContaining(search.trim(), pageable);
            } else {
                // All materials with limit
                materials = materialRepository.findAllActiveMaterialNamesWithIds(pageable);
            }

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materials);
        } catch (Exception e) {
            log.error("Error fetching material names with IDs for typeahead", e);
            return ApiResponse.error("Failed to fetch material names with IDs");
        }
    }

    private void preprocessMaterial(Material material) {
        // Trim name
        if (material.getName() != null) {
            material.setName(material.getName().trim());
        }

        // Trim unit
        if (material.getUnit() != null) {
            String trimmedUnit = material.getUnit().trim();
            material.setUnit(trimmedUnit.isEmpty() ? null : trimmedUnit);
        }
    }

}